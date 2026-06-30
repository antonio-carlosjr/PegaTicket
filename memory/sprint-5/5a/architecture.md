# Sprint 5 — Trilha 5A (Financeiro) — Arquitetura Tecnica

> Escopo desta trilha: **US-043** (repasse pos-evento REALIZADO) + **US-042** (reembolso em massa por evento CANCELADO) + wiring AMQP (`evento.finalizado` ja declarada; **`evento.cancelado` nova**) + a **primeira mensageria do event-service** + **TECH-S4-01** (`evento_id`/`promotor_id` em `pagamentos`).
> NAO inclui: reembolso por cancelamento do PARTICIPANTE (US-035 = 5B), check-in (5B), avaliacoes (5B), carga (5C). O mecanismo de reembolso e projetado **reusavel** para 5B, mas so o caminho EVENTO_CANCELADO e implementado aqui.

## Historias cobertas
- **US-043** — repasse (−10%) aos pagamentos CONFIRMADO de um evento que vira REALIZADO.
- **US-042** (parte) — reembolso em massa quando o promotor cancela o evento (payment estorna + ticket cancela inscricoes/ingressos/libera vagas).
- **TECH-S4-01** (embutido) — persistir `evento_id`/`promotor_id` em `pagamentos` (necessario para o repasse saber **quais** pagamentos e **para quem**).

## Servico(s) afetado(s)
| Servico | Papel na 5A | Mudanca principal |
|---|---|---|
| **event-service** | Origem das transicoes de status e **produtor** dos 2 eventos | `Evento.realizar()` (guard PUBLICADO→REALIZADO); endpoint `POST /events/{id}/encerrar`; **primeira RabbitConfig + EventoPublisher** (publica em `afterCommit`); migration **V3** (`realizado_em`, `cancelado_em`). |
| **payment-service** | **Consumidor** de `evento.finalizado` (repasse) **e** `evento.cancelado` (reembolso em massa) | migration **V3** (`pagamentos += evento_id, promotor_id`); `Pagamento.repassar()`/`reembolsar()`; `Reembolso.criar(...)` (passa a ser escrito); 2 listeners idempotentes; queries por `evento_id+status`. |
| **ticket-service** | **Consumidor** de `evento.cancelado` (cancela inscricoes + ingressos + libera vagas) | listener `evento.cancelado` idempotente; `Inscricao.cancelar()`/`Ingresso.cancelar()`; chamada interna `liberar-vaga` ou zerar via reset de vagas. **Sem migration** (status CANCELADA/CANCELADO ja existem). |
| **infra** | Topologia | **`evento.cancelado`** + `evento.cancelado.dlq` + bindings em `definitions.json`. (`evento.finalizado` ja existe.) |
| **frontend** | (fora desta trilha — UI de extrato ja reflete status via `/payments/me`) | Sem tela nova obrigatoria; `GET /payments/me` ja mostra `REPASSADO`/`REEMBOLSADO`. Botao "Encerrar evento" do promotor e desejavel mas opcional (PO valida). |

## Decisao do gatilho REALIZADO (ADR — ver §Estrategias e decisions.md)
**Endpoint do promotor `POST /events/{id}/encerrar`** (PUBLICADO→REALIZADO), **mais** nota de job futuro.
- Justificativa: e **demonstravel na banca** (Marina clica "Encerrar" e ve o repasse no extrato em segundos), nao depende de relogio nem de `data_fim` chegar, e e simetrico ao `POST /events/{id}/cancelar` ja existente. Um `@Scheduled` que marca `data_fim < now → REALIZADO` fica **documentado como evolucao** (mesmo padrao do `ExpiracaoReservaJob` do S4), mas nao entra na 5A para nao introduzir nao-determinismo temporal nos testes nem risco de finalizar evento antes da hora.
- Guard: `Evento.realizar()` so transita **PUBLICADO→REALIZADO**; RASCUNHO/CANCELADO/REALIZADO lancam `TRANSICAO_INVALIDA` (409) — espelha `cancelar()`/`publicar()`.

## Eventos de dominio (AMQP) — detalhe em api-contracts.md §AMQP
Exchange `ticketeira.events` (topic). Produtor **event-service** (novo). `eventId` (UUID) gerado na ORIGEM = chave de idempotencia (ADR-T11).

| Routing key | Produtor | Consumidor(es) | Efeito |
|---|---|---|---|
| `evento.finalizado` *(fila ja existe)* | event | payment | repasse: cada `pagamento CONFIRMADO` do `eventoId` → `REPASSADO` (escrow liberado; `valor_repasse` ja = bruto−10% do S4). |
| `evento.cancelado` *(fila NOVA)* | event | payment **e** ticket | payment: `CONFIRMADO → REEMBOLSADO` + `reembolsos(EVENTO_CANCELADO)`; ticket: inscricoes `ATIVA|PENDENTE_PAGAMENTO → CANCELADA` + ingressos `→ CANCELADO` + libera vagas. |

> Idempotencia: `processed_events(event_id UUID PK)` em **cada consumidor** (payment ja tem a tabela; ticket ja tem). Publish em `afterCommit` (`TransactionSynchronization`). event-service **nao consome** nada → **nao precisa** de `processed_events`.

## Fluxos / sagas (diagrama textual)

### REPASSE (US-043)
```
Promotor → POST /api/events/{id}/encerrar  (PROMOTOR + owner)
  event-service: carregarComOwnership → Evento.realizar() (PUBLICADO→REALIZADO)
                 set realizado_em = now; save; afterCommit:
                 publica evento.finalizado { eventId(UUID), eventoId, promotorId, ocorridoEm }
  ───────────────────────────────────────────────────────────────────
  payment-service @RabbitListener(evento.finalizado):  [tx unica]
     INSERT processed_events(eventId)         ← PK colide? = reentrega → ACK no-op (return)
     UPDATE pagamentos SET status='REPASSADO', repassado_em=now
            WHERE evento_id=:eventoId AND status='CONFIRMADO'   ← transicao condicional em massa
     (valor_repasse ja computado no S4; nada a recalcular)
     log "repasse aplicado a N pagamentos"
```
Invariante: pagamento **nao-CONFIRMADO** (PENDENTE/REEMBOLSADO/ja-REPASSADO) **nao e tocado** (clausula `status='CONFIRMADO'`). Reentrega → `processed_events` PK colide → no-op. Resultado: **1 repasse por pagamento**.

### REEMBOLSO EM MASSA por evento cancelado (US-042)
```
Promotor → POST /api/events/{id}/cancelar  (ja existe; PROMOTOR + owner)
  event-service: Evento.cancelar() (RASCUNHO|PUBLICADO→CANCELADO; guard ja existe)
                 set cancelado_em = now; save; afterCommit:
                 publica evento.cancelado { eventId(UUID), eventoId, promotorId, ocorridoEm }
  ──────────────────────── fan-out (2 consumidores independentes) ────────────────────────
  payment-service @RabbitListener(evento.cancelado):  [tx unica]
     INSERT processed_events(eventId)         ← reentrega → ACK no-op
     SELECT pagamentos WHERE evento_id=:eventoId AND status='CONFIRMADO'  (FOR UPDATE)
     para cada p:
        p.reembolsar()  → status='REEMBOLSADO', reembolsado_em=now
        INSERT reembolsos(pagamento_id=p.id, usuario_id=p.usuario_id,
                          valor=p.valor_bruto, motivo='EVENTO_CANCELADO', status='PROCESSADO')
     (UPDATE em massa condicional WHERE status='CONFIRMADO' como caminho equivalente;
      ver Estrategia de concorrencia — preferimos o SELECT...FOR UPDATE + loop porque
      precisamos inserir 1 reembolso por pagamento)

  ticket-service @RabbitListener(evento.cancelado):  [tx unica, dedicado processed_events]
     INSERT processed_events(eventId)         ← reentrega → ACK no-op
     UPDATE inscricoes SET status='CANCELADA'
            WHERE evento_id=:eventoId AND status IN ('ATIVA','PENDENTE_PAGAMENTO')
     UPDATE ingressos SET status='CANCELADO'
            WHERE inscricao_id IN (inscricoes do evento) AND status='ATIVO'
     liberar vagas: o evento esta CANCELADO no event-service → o ticket NAO precisa
        chamar liberar-vaga (vagas presas num evento cancelado nao importam:
        ninguem mais se inscreve). Ver §Vagas no cancelamento.
```
Resultado: pagamentos `REEMBOLSADO` (1 reembolso por pagamento), inscricoes/ingressos `CANCELADO`, idempotente. Cross-service consistente por `processed_events` em ambos.

### CORRIDA repasse-vs-reembolso no mesmo pagamento
```
Cenario raro: evento.finalizado e evento.cancelado chegam quase juntos para o mesmo eventoId.
  (so possivel por bug de produto — encerrar e cancelar o mesmo evento — mas a saga deve ser segura)
  Ambos competem pela transicao do MESMO pagamento CONFIRMADO.
  Defesa: as duas transicoes sao CONDICIONAIS em `status='CONFIRMADO'`.
    - Se REPASSADO vence: o reembolso encontra status!='CONFIRMADO' → nao toca (0 linhas).
    - Se REEMBOLSADO vence: o repasse encontra status!='CONFIRMADO' → nao toca (0 linhas).
  O row lock do Postgres (UPDATE...WHERE / SELECT FOR UPDATE) serializa as duas tx.
  Garantia: cada pagamento termina em EXATAMENTE UM de {REPASSADO, REEMBOLSADO}; nunca os dois.
```

## Estrategias criticas

### Concorrencia
| Cenario | Estrategia |
|---|---|
| Repasse em massa (1 evento, N pagamentos) | `UPDATE pagamentos SET status='REPASSADO',... WHERE evento_id=:e AND status='CONFIRMADO'` — **1 UPDATE condicional** (O(1) round-trips, row locks pelo Postgres). Sem N+1. |
| Reembolso em massa (1 evento, N pagamentos) | `SELECT ... WHERE evento_id=:e AND status='CONFIRMADO' FOR UPDATE` + loop inserindo 1 `reembolsos` por pagamento + `reembolsar()`. (Precisa do INSERT por linha → loop; query unica para carregar, sem N+1 no SELECT.) |
| Repasse vs reembolso mesmo pagamento | Transicoes **condicionais** `WHERE status='CONFIRMADO'`; row lock serializa → so um vence, o outro e no-op (0 linhas). |
| `evento.finalizado`/`evento.cancelado` reentregue | `processed_events(eventId)` PK em cada consumidor; 2a entrega = PK colide → tx desfaz → **ACK no-op** (ADR-T11). |
| Cancelar evento 2x | `Evento.cancelar()` ja guarda (`EVENTO_JA_CANCELADO` 409) → so 1 `evento.cancelado` publicado. |
| Encerrar evento 2x | `Evento.realizar()` guarda (`TRANSICAO_INVALIDA` se ja REALIZADO) → so 1 `evento.finalizado`. |
| "Evento tarde demais" (pagamento ja em estado avancado) | Listener NAO deixa transicao lancar: a clausula `WHERE status='CONFIRMADO'` simplesmente nao casa (0 linhas) → ACK normal. **Nunca** poison message (ADR-T11 / CR-S4-01). |

### Vagas no cancelamento (decisao)
Quando o **evento e cancelado**, `eventos.status=CANCELADO` no event-service → o decremento de vaga (`reservarVaga`) so atua em `status=PUBLICADO`, logo **ninguem mais reserva**. As `vagas_disponiveis` do evento cancelado tornam-se irrelevantes. Portanto o ticket-service **nao chama `liberar-vaga`** no consumo de `evento.cancelado` (seria I/O de rede inutil dentro do fan-out e introduziria acoplamento ticket→event no caminho de cancelamento). O criterio do PO "vagas liberadas (contagem volta a capacidade)" e satisfeito **conceitualmente** pelo proprio status CANCELADO; se o PO exigir `vagas_disponiveis = capacidade` literalmente no banco, o event-service zera/reseta `vagas_disponiveis = capacidade` na propria transicao `cancelar()` (mutacao local, sem I/O cross-service). **Recomendacao:** resetar `vagas_disponiveis = capacidade` em `Evento.cancelar()` (1 linha, local, deixa o banco coerente com o criterio do PO). **PO valida** (ver §Para o PO).

### Idempotencia
- `processed_events` em payment (existe, V2) e ticket (existe, V2). event-service **nao** ganha a tabela (so produz).
- `eventId` UUID gerado no `EventoPublisher` (origem) e carregado no payload.
- Defesa em profundidade no reembolso: a tabela `reembolsos` nao tem UNIQUE(pagamento_id) hoje; a idempotencia vem do `processed_events` + da transicao condicional (so cria reembolso para pagamento que estava CONFIRMADO — na 2a entrega ja esta REEMBOLSADO, logo o SELECT nao o traz). **Opcional (defesa extra):** `CREATE UNIQUE INDEX uk_reembolso_pagamento_evento ON reembolsos(pagamento_id) WHERE motivo='EVENTO_CANCELADO'` — barra reembolso duplicado mesmo com `eventId` diferente. Incluido como item opcional na migration (ver data-model.md).

### Performance / indices
- Repasse e reembolso filtram `pagamentos` por `evento_id` → **novo indice** `idx_pagamentos_evento ON pagamentos(evento_id)` (V3). Combinado com `status` (ja indexado) cobre `WHERE evento_id=? AND status='CONFIRMADO'`.
- ticket: `UPDATE inscricoes WHERE evento_id=?` usa `idx_inscricoes_evento` (ja existe, V1).

### Seguranca / auth
- `POST /events/{id}/encerrar`: `requireUserId` + `requirePromotor(papel)` + `carregarComOwnership` — **identico** ao `cancelar()` existente (PROMOTOR + dono do evento). `X-User-Papel` ja e injetado pelo gateway (`JwtAuthGlobalFilter` linha 67) e ja e lido pelo `EventController`. **Sem divida nova de auth.**
- Consumidores AMQP nao tem auth de usuario (sao internos ao broker); a autorizacao e o proprio fato de a mensagem so ser publicada por uma transicao de estado autorizada na origem.
- `evento.cancelado`/`evento.finalizado` nao carregam dados de usuario — so `eventoId`/`promotorId` (ja conhecidos do evento), sem PII.

## Componentes backend (delta)
- **event-service:** `RabbitConfig` (NOVA — exchanges/filas/bindings + `MessageConverter`, delegando RabbitTemplate ao autoconfig), `EventoPublisher` (publica `evento.finalizado`/`evento.cancelado` em afterCommit), `EventoFinalizadoEvent`/`EventoCanceladoEvent` (records), `Evento.realizar()`, `EventService.encerrar(...)`, `EventController` `POST /{id}/encerrar`. Migration V3.
- **payment-service:** `Pagamento` += `eventoId`/`promotorId` + `repassar()`/`reembolsar()`; `Pagamento.pendente(...)` ganha `eventoId`/`promotorId`; `criarPendente` passa-os do `PedidoCriadoEvent` (ja os recebe); `Reembolso.criar(...)`; `EventoFinalizadoListener`/`EventoCanceladoListener`; `RepasseService`/`ReembolsoService` (ou metodos no `PagamentoService`); `PagamentoRepository` queries por `evento_id`+status; RabbitConfig += filas `evento.finalizado` (ja) + `evento.cancelado` (nova) + DLQs. Migration V3.
- **ticket-service:** `EventoCanceladoListener`; `Inscricao.cancelar()`; `Ingresso.cancelar()`; `CancelamentoEventoService` (atualiza em massa); `InscricaoRepository`/`IngressoRepository` queries por `evento_id`; RabbitConfig += fila `evento.cancelado` + DLQ. Sem migration.
- **Reuso:** `processed_events` (ambos), `GlobalExceptionHandler`, padrao `afterCommit`/`TransactionSynchronization`, `TestcontainersBase` (singleton).

## Padrao de RabbitConfig (aprendizado S4 — obrigatorio)
- **Delegar ao autoconfigure:** NAO declarar `RabbitTemplate` proprio; NAO usar `@ConditionalOnBean(ConnectionFactory.class)`. Declarar so o `Jackson2JsonMessageConverter` (com `JavaTimeModule` p/ `OffsetDateTime`) + exchanges/filas/bindings. O Boot aplica o converter ao template e a fabrica de listeners.
- **event-service** ganha RabbitMQ pela primeira vez: adicionar `spring-boot-starter-amqp` ao `pom.xml`; `application.yml` com `spring.rabbitmq.*`; perfis Postgres-only excluem `RabbitAutoConfiguration` via `@DynamicPropertySource`/`spring.autoconfigure.exclude` e mockam o publisher; perfil `test-postgres` NAO exclui (testes de listener precisam do broker real).
- Nao usar `@Profile("!test")` na config nova (o padrao canonico e a config inerte sem broker, como no payment `RabbitConfig`); manter consistencia para nao reintroduzir o bug de beans pulados.

## Riscos tecnicos
| Risco | Prob | Impacto | Mitigacao |
|---|---|---|---|
| event-service nunca teve AMQP → config/pom/yml errados (1ª vez) | Media | event nao publica; saga nao dispara | Espelhar EXATAMENTE o `RabbitConfig` do payment (delegacao ao autoconfig); teste de integracao Testcontainers que captura a mensagem na fila `evento.finalizado`/`evento.cancelado`. |
| `evento_id` nulo em pagamentos antigos (linhas pre-V3) | Media | repasse/reembolso ignora pagamentos legados | Migration V3 deixa coluna NULLABLE; pagamentos novos (pos-V3) sempre populados via `pedido.criado` (que ja carrega `eventoId`). Pagamentos de teste/demo sao criados pos-V3. Documentar: repasse so cobre pagamentos com `evento_id` preenchido. |
| Reembolso em massa nao idempotente em reentrega com eventId diferente | Baixa | reembolso duplicado | `processed_events` cobre reentrega normal; UNIQUE parcial opcional em `reembolsos(pagamento_id)` cobre reprocessamento manual. |
| Corrida repasse/reembolso deixa pagamento em 2 estados | Baixa | inconsistencia financeira | Transicoes condicionais `WHERE status='CONFIRMADO'` + row lock → exatamente 1 vence (testado). |
| Testcontainers nao roda no Windows local (so CI) | Alta (ambiente) | testes de saga so verdes no CI | Padrao S4: `@Testcontainers(disabledWithoutDocker=true)` + `TestcontainersBase` singleton + `@BeforeEach` purga filas. Local roda os unit/H2; CI roda a saga. |

## Definition of Done tecnico
- [ ] Migration payment V3 (`evento_id`/`promotor_id` + indice) e event V3 (`realizado_em`/`cancelado_em`) aplicaveis (Flyway).
- [ ] `Evento.realizar()` com guard; `POST /events/{id}/encerrar` autorizado (PROMOTOR + owner).
- [ ] event-service publica `evento.finalizado`/`evento.cancelado` em afterCommit (rollback nao publica — testado).
- [ ] payment: repasse condicional (`status='CONFIRMADO'`) idempotente; reembolso em massa + `reembolsos(EVENTO_CANCELADO)`.
- [ ] ticket: cancela inscricoes/ingressos do evento; idempotente.
- [ ] `criarPendente` persiste `evento_id`/`promotor_id` (TECH-S4-01 fechado).
- [ ] cobertura ≥90% nos services de repasse/reembolso; sem N+1.
- [ ] Testcontainers PG+Rabbit verdes no CI (reentrega → 1x; corrida → 1 vencedor).
- [ ] OpenAPI/Swagger do `encerrar` atualizado. `./mvnw verify` verde.

## Para o PO validar (Fase 3)
1. **Gatilho REALIZADO = endpoint `POST /events/{id}/encerrar`** (com job como evolucao futura) — confirmar que e suficiente para a demo (Marina clica "Encerrar").
2. **Vagas no cancelamento:** resetar `vagas_disponiveis = capacidade` em `Evento.cancelar()` para o banco refletir literalmente o criterio "vagas voltam a capacidade", **ou** aceitar que o status CANCELADO ja torna as vagas irrelevantes (sem reset). Recomendacao do Arquiteto: resetar (1 linha, banco coerente).
3. **Escopo do reembolso na 5A:** apenas `EVENTO_CANCELADO` (em massa). `CANCELAMENTO_PARTICIPANTE` (US-035) fica para 5B — confirmar.
4. **UNIQUE parcial opcional em `reembolsos`** (defesa extra anti-duplicata) — incluir ou deixar so o `processed_events`?
