# Sprint 4 — Arquitetura Tecnica

> Saga de inscricao **paga** assincrona (RabbitMQ), idempotente e a prova de concorrencia. Escrow puro.
> Fonte de escopo: `00-sprint-spec.md` + `po-planning.md`. ADRs novos no fim de `memory/project/decisions.md` (ADR-T10, ADR-T11) + atualizacao do ADR-T04.

## Historias cobertas
- **US-060** — consumidores RabbitMQ idempotentes (`processed_events`) ligados; produtor publica em `afterCommit`.
- **US-040** — pagar evento PAGO via gateway **simulado** com retencao em **escrow** (`pagamentos.status=CONFIRMADO`, taxa 10% + repasse computados e nao liberados).
- **US-041** — emitir ingresso **somente apos** `pagamento.aprovado` (saga assincrona); estado intermediario `PENDENTE_PAGAMENTO`.

## Servico(s) afetado(s)
| Servico | Mudanca |
|---|---|
| **ticket-service** | Inscricao PAGO: novo estado `PENDENTE_PAGAMENTO` (+ `EXPIRADA`); a saga sincrona da S3 (`InscricaoService`) ganha um **ramo PAGO** que NAO emite ingresso e publica `pedido.criado` em `afterCommit`; novo consumidor de `pagamento.aprovado` que emite ingresso + ativa inscricao (idempotente via `processed_events`); job agendado de expiracao de reserva (TTL). |
| **payment-service** | Sai de stub -> real (espelha user-service): entidades `Pagamento`/`ConfiguracaoPlataforma`/`ProcessedEvent` (+ `Reembolso` read-only), consumidor `pedido.criado` (cria `Pagamento(PENDENTE)` + escrow), endpoint `POST /payments/{inscricaoId}/confirmar` (gateway SIMULADO aprova), produtor `pagamento.aprovado` em `afterCommit`, consultas do usuario/admin. |
| **event-service** | `EventoInternoResponse` passa a expor `preco` e `promotorId`. **Sem migration** (colunas ja existem em `eventos` V1). |
| **infra** | `definitions.json` ja tem `ticketeira.events` (topic) + filas `pedido.criado` / `pagamento.aprovado` (+ DLX/DLQ). **Sem alteracao de topologia.** |

## Modelo de dados (delta) -> detalhe em `data-model.md`
- **ticket V2** (`V2__saga_pagamento.sql`): amplia o CHECK de `inscricoes.status` para `('ATIVA','CANCELADA','PENDENTE_PAGAMENTO','EXPIRADA')`; cria `processed_events(event_id UUID PK, routing_key, processado_em)`; indice parcial `idx_inscricoes_pendentes` para o job de TTL.
- **payment V2** (`V2__idempotencia.sql`): cria `processed_events(event_id UUID PK, routing_key, processado_em)`. (`pagamentos`/`reembolsos`/`configuracao_plataforma` ja em V1; `pagamentos.inscricao_id` ja e `UNIQUE`.)
- **Reuso de constraints existentes como rede final:** `ingressos.inscricao_id UNIQUE` (1 ingresso/inscricao) e `pagamentos.inscricao_id UNIQUE` (1 pagamento/inscricao).

## Endpoints novos/alterados -> detalhe em `api-contracts.md`
- `POST /api/inscricoes` (ticket) — **alterado**: aceita evento PAGO; cria `PENDENTE_PAGAMENTO`, **nao** emite ingresso, retorna referencia de pagamento. Fluxo GRATUITO **intacto**.
- `POST /api/payments/{inscricaoId}/confirmar` (payment) — **novo**: gateway SIMULADO confirma, publica `pagamento.aprovado`. Idempotente.
- `GET /api/payments/me` e `GET /api/payments/inscricao/{inscricaoId}` (payment) — **novos**: status do pagamento do usuario.
- `GET /api/payments` (payment) — **novo**: listagem para Admin (auditoria de escrow).
- `GET /internal/events/{id}` (event) — **alterado**: inclui `preco` e `promotorId`. Autorizado por `X-Internal-Token` (ADR-T08), nunca pelo gateway.

## Eventos de dominio (AMQP) — exchange `ticketeira.events` (topic)
| Routing key | Produtor | Consumidor | Payload (campos-chave) |
|---|---|---|---|
| `pedido.criado` | ticket-service | payment-service | `eventId` (UUID, dedup), `inscricaoId`, `usuarioId`, `eventoId`, `valor` (NUMERIC), `promotorId`, `ocorridoEm` |
| `pagamento.aprovado` | payment-service | ticket-service | `eventId` (UUID, dedup), `pagamentoId`, `inscricaoId`, `usuarioId`, `eventoId`, `ocorridoEm` |
> `eventId` e gerado na **origem** (produtor) com `UUID.randomUUID()` e e a chave de idempotencia no consumidor. **Produtor publica so em `afterCommit`** (`TransactionSynchronization`) — nunca emite se a tx local deu rollback.

## Componentes backend

### ticket-service
- `service/InscricaoService` — ramo PAGO em `inscrever()`: valida (PUBLICADO, **PAGO**), pre-check dup, `reservarVaga` (ADR-T07), tx local cria `Inscricao(PENDENTE_PAGAMENTO)` **sem ingresso**, registra `afterCommit` que publica `pedido.criado`. Ramo GRATUITO inalterado (emite ingresso na hora).
- `messaging/PedidoCriadoPublisher` — `RabbitTemplate` para `ticketeira.events` / rk `pedido.criado`; chamado via `TransactionSynchronizationManager.registerSynchronization(...afterCommit...)`.
- `messaging/PagamentoAprovadoListener` — `@RabbitListener(queues="pagamento.aprovado")`: idempotencia (`processed_events`) -> emite `Ingresso` + `Inscricao -> ATIVA` (na mesma tx). Falha nao tratada -> DLQ.
- `service/ExpiracaoReservaJob` — `@Scheduled` (fixedDelay): expira `PENDENTE_PAGAMENTO` mais velhas que `app.reserva.ttl-min` -> `EXPIRADA` + `liberarVaga` (compensacao). Idempotente.
- `config/RabbitConfig` — `Jackson2JsonMessageConverter`, exchange/bindings declarados via `definitions.json` (sem auto-declare divergente).
- Reuso: `EventClient`/`EventResumo` (campo `preco` novo), `GlobalExceptionHandler`, `BusinessException`/`NotFoundException`.

### payment-service (de stub a real — espelha user-service)
- `domain/` — `Pagamento` (`@Entity`, factory `pendente(...)` + `confirmar(gatewayId)` idempotente), `StatusPagamento` (`PENDENTE,CONFIRMADO,REEMBOLSADO,REPASSADO`), `ConfiguracaoPlataforma` (read), `ProcessedEvent`, `Reembolso` (read-only).
- `service/PagamentoService` — `criarPendente(PedidoCriado)` (idempotente; calcula escrow); `confirmar(inscricaoId, usuarioId)` (gateway SIMULADO; transicao idempotente; `afterCommit` publica `pagamento.aprovado`); consultas.
- `gateway/GatewaySimulado` — `aprovar(valor)` retorna `gatewayPaymentId` (UUID "SIM-..."); ponto unico para futura troca por gateway real.
- `messaging/PedidoCriadoListener` (`@RabbitListener(queues="pedido.criado")`) + `PagamentoAprovadoPublisher` (`afterCommit`).
- `controller/PaymentController` — substitui o stub: `confirmar`, `me`, `inscricao/{id}`, listagem admin. Le `X-User-Id` (gateway).
- `config/RabbitConfig`, `OpenApiConfig` (Swagger publicado — criterio 6).

### event-service
- `dto/EventoInternoResponse` — +`preco BigDecimal`, +`promotorId Long`; `from(Evento)` mapeia `e.getPreco()`/`e.getPromotorId()` (acessores existem).

## Componentes frontend
- `api/payments.ts` — `getPagamentoDaInscricao(inscricaoId)`, `confirmarPagamento(inscricaoId)`, `listarMeusPagamentos()` (axios via `client.ts`).
- `pages/CheckoutPage.tsx` (rota `/checkout/:inscricaoId`) — estados: `PENDENTE_PAGAMENTO` (botao "Pagar", 1 toque) -> apos confirmar, **polling** de "Meus ingressos" ate o ingresso aparecer (feedback assincrono, risco R3 do PO). Erro de gateway -> toast amigavel (nao stack trace).
- Fluxo de inscricao: ao inscrever em evento PAGO, redireciona para `/checkout/:inscricaoId` ("Pagamento pendente"); GRATUITO segue direto para "Meus ingressos" (intacto).
- `pages/MeusIngressos` — exibe estado "aguardando confirmacao de pagamento" para inscricoes `PENDENTE_PAGAMENTO` sem ingresso.
- (Admin) tela/lista de pagamentos com `valor_bruto`/`valor_taxa`/`valor_repasse` e status `CONFIRMADO`.
- Schemas Zod alinhados aos contratos; sem `any`.

## Fluxo da saga (diagrama textual)

```
Bruno -> POST /api/inscricoes { eventoId } (evento PAGO)
  ticket: getEvento (PUBLICADO, PAGO, preco) -> pre-check dup -> reservarVaga (UPDATE atomico, ADR-T07)
          -> tx local: Inscricao(PENDENTE_PAGAMENTO)  [SEM ingresso]
          -> afterCommit: publica pedido.criado { eventId=UUID, inscricaoId, usuarioId, eventoId, valor=preco, promotorId }
  201 { inscricaoId, status=PENDENTE_PAGAMENTO, pagamento.status=AGUARDANDO }

  payment: <- consome pedido.criado
           processed_events.contains(eventId)? sim -> ACK no-op (idempotente)
           nao -> tx: INSERT processed_events(eventId) + Pagamento(PENDENTE, escrow)
                  valor_bruto=valor; valor_taxa=round(valor*0.10,2); valor_repasse=bruto-taxa
                  (UNIQUE inscricao_id: se ja existe -> ja processado, ACK)

Bruno -> POST /api/payments/{inscricaoId}/confirmar   (gateway SIMULADO aprova)
  payment: carrega Pagamento; se CONFIRMADO -> no-op idempotente, NAO republica
           se PENDENTE -> gateway.aprovar() -> status=CONFIRMADO (retido/escrow), processado_em=now
           -> afterCommit: publica pagamento.aprovado { eventId=UUID, pagamentoId, inscricaoId, usuarioId, eventoId }
  200 { status=CONFIRMADO }

  ticket: <- consome pagamento.aprovado
          processed_events.contains(eventId)? sim -> ACK no-op
          nao -> tx: INSERT processed_events(eventId) + Ingresso.emitir(inscricaoId) + Inscricao -> ATIVA
                 (UNIQUE ingressos.inscricao_id: rede final contra 2a entrega com eventId diferente)

Bruno: ingresso com QR aparece em "Meus ingressos" (frontend faz polling).
```

## Estrategias criticas

### Concorrencia
| Cenario | Estrategia |
|---|---|
| **Ultima vaga em evento PAGO** (K inscricoes simultaneas) | `reservarVaga` = `UPDATE eventos SET vagas_disponiveis = vagas_disponiveis - 1 WHERE id=:id AND status=PUBLICADO AND vagas_disponiveis > 0` (ADR-T07) **antes** do pagamento. Row lock serializa: 1 reserva, K-1 -> 409 `EVENTO_ESGOTADO`. Nenhuma das K-1 chega a criar `Pagamento`. |
| **`pagamento.aprovado` entregue 2x** (at-least-once) | `processed_events(eventId)` no ticket-service (PK rejeita 2a) + `ingressos.inscricao_id UNIQUE` como rede final -> **1 ingresso**. |
| **`pedido.criado` entregue 2x** | `processed_events(eventId)` no payment-service + `pagamentos.inscricao_id UNIQUE` -> **1 pagamento**. |
| **Confirmar pagamento 2x** (mesmo `inscricaoId`) | Transicao idempotente: `PENDENTE -> CONFIRMADO`; se ja `CONFIRMADO`, no-op e **NAO** republica `pagamento.aprovado` (1 unico evento). Tx serializa via lock pessimista na linha de `pagamentos` (`@Lock(PESSIMISTIC_WRITE)` no `findByInscricaoId`) para evitar dupla confirmacao concorrente. |
| **Evento sem rollback fantasma** | Produtor publica so em `afterCommit` — se a tx local fizer rollback, o `afterCommit` nao roda, evento nao sai. |

### Idempotencia (US-060)
- Toda chave de idempotencia e o `eventId` (UUID gerado na **origem**, no payload). Consumidor: dentro da **mesma tx** do efeito, `INSERT INTO processed_events(event_id, routing_key)`; PK colide na 2a entrega -> a tx desfaz o efeito e o consumidor faz **ACK** (mensagem consumida, sem efeito duplo). Padrao em ambos os servicos (ADR-T11).
- **Defesa em profundidade:** as constraints UNIQUE (`ingressos.inscricao_id`, `pagamentos.inscricao_id`) garantem exactly-once-effect mesmo num cenario de `eventId` diferente para o mesmo `inscricaoId` (ex.: reprocessamento manual).
- **Producao apos commit:** `TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){ afterCommit(){ rabbitTemplate.convertAndSend(...) } })`. Teste forca rollback antes do commit e verifica que **nada** e publicado (criterio US-060.3).

### Performance
- `processed_events` consulta por PK (`event_id UUID`) -> O(1).
- Job de expiracao: `idx_inscricoes_pendentes` (indice parcial `WHERE status='PENDENTE_PAGAMENTO'`) -> varredura so das pendentes, sem full scan.
- `meus ingressos` / consultas de pagamento sem N+1 (join fetch / projecao; padrao S3 mantido).
- Confirmacao de pagamento: `findByInscricaoId` usa `pagamentos.inscricao_id UNIQUE` (B-tree) -> O(1).

### Seguranca / auth
- Endpoints de pagamento do usuario leem `X-User-Id` (gateway). Consulta de pagamento confere **ownership** (`pagamento.usuarioId == X-User-Id`) -> 404 se nao for dono (nao vaza existencia).
- `GET /api/payments` (Admin/auditoria): **divida ADR-T01** — papel ja vai no token desde a S1 (US-051), entao **protegido por papel ADMIN** via `X-User-Papel` (header injetado pelo gateway). Confirmar com PO que o header esta disponivel; senao, marcar divida e liberar para qualquer autenticado em homolog.
- `GET /internal/events/{id}` segue ADR-T08: `X-Internal-Token` constante-no-tempo, **nao** roteado pelo gateway (externo -> 404; sem token -> 403). Expor `preco`/`promotorId` no canal interno e seguro (so o ticket-service chama).
- Confirmar pagamento exige `X-User-Id == pagamento.usuarioId` (Bruno so paga o proprio pedido).

## Tratamento de erro / compensacao
- **Inscricao PAGO falha na tx local apos reservar vaga** -> mesma compensacao da S3 (`liberarVaga`; se compensacao falhar, log `[RECONCILIACAO]`).
- **Pagamento abandonado (Bruno nao confirma)** -> `ExpiracaoReservaJob` (TTL `app.reserva.ttl-min`, default 30 min): `Inscricao(PENDENTE_PAGAMENTO) -> EXPIRADA` + `liberarVaga`. Idempotente (so age em pendentes vencidas). **Decisao ADR-T10.**
- **Consumidor falha** (ex.: erro transitorio) -> excecao propaga -> RabbitMQ re-entrega; apos N tentativas -> DLQ (`*.dlq` ja declaradas). Idempotencia garante que re-entrega nao duplica.
- **Gateway SIMULADO devolve erro** -> `Pagamento` permanece `PENDENTE`, endpoint retorna erro tipado (`PAGAMENTO_RECUSADO`, 402) -> frontend mostra mensagem amigavel; Bruno pode tentar de novo (idempotente).

## Riscos tecnicos
| Risco | Prob | Impacto | Mitigacao |
|---|---|---|---|
| R1 — Vaga presa em pagamento abandonado | Media | Alto (capacidade real do evento) | TTL via `@Scheduled` libera vaga + `EXPIRADA` (ADR-T10). |
| R2 — Testes de consumidor exigem broker real | Alta | Medio (flakiness/CI) | **Testcontainers RabbitMQ + Postgres**, padrao "skip local sem Docker" (`@Testcontainers(disabledWithoutDocker=true)`); roda no CI. |
| R3 — Sprint mais complexo (2 servicos + saga + front) | Alta | Medio | Reembolso/repasse FORA; caminho-feliz primeiro; payment espelha user-service. |
| R4 — Exactly-once-effect com at-least-once | Media | Alto | `processed_events` + UNIQUE como rede final; teste de reentrega obrigatorio. |
| R5 — Feedback assincrono (Bruno espera) | Media | Medio (UX) | Frontend faz polling de "Meus ingressos" pos-confirmacao; aceite exige ingresso sem reload manual. |
| R6 — Job de TTL concorre com confirmacao tardia | Baixa | Medio (vaga liberada + pagamento confirmado) | Job so expira pendentes > TTL; confirmacao tardia de uma `EXPIRADA` retorna `INSCRICAO_EXPIRADA` (409) sem emitir ingresso. Documentado como aceitavel no escopo academico. |

## Definition of Done tecnico
- [ ] Migrations ticket V2 + payment V2 aplicaveis (Flyway) e validadas por Hibernate (`ddl-auto: validate`).
- [ ] Cobertura >= 80% em `PagamentoService` e `InscricaoService` (ramo PAGO).
- [ ] Sem N+1 nas consultas de pagamento/ingresso.
- [ ] Concorrencia testada **em Postgres real** (ultima vaga PAGO) + idempotencia testada **em RabbitMQ real** (reentrega) via Testcontainers.
- [ ] `afterCommit` nao publica em rollback (teste unitario).
- [ ] OpenAPI/Swagger do payment-service publicado.
- [ ] Fluxo GRATUITO da S3 intacto (regressao verde).
- [ ] `./mvnw -B -ntp verify` verde + `npm run test:run` verde.
