# Sprint 5 — Trilha 5B (Experiencia) — Arquitetura Tecnica

> Escopo desta trilha: **US-034** check-in por QR (ticket) + **US-035** cancelar inscricao c/ politica de prazo + reembolso individual (ticket -> payment) + **US-024** avaliar evento (event) + **US-025** reputacao no detalhe (event).
> NAO inclui: repasse/reembolso em massa (5A, ja mergeado), carga (5C), observabilidade/hardening (5C propostos).
> **Reuso maximo da 5A:** o reembolso individual REUSA `Pagamento.reembolsar()` + `Reembolso.criar(...)` + `processed_events` (nao reescreve o mecanismo). So o caminho `CANCELAMENTO_PARTICIPANTE` e novo.

## Historias cobertas
- **US-034** — promotor dono valida `codigo_unico` na porta: `ingressos ATIVO -> UTILIZADO` + cria `checkins` (`UNIQUE(ingresso_id)` impede duplo). 409 no 2o; 403 evento alheio / papel errado; 404 codigo inexistente/cancelado.
- **US-035** — participante cancela a propria inscricao dentro da politica (`prazo_reembolso_dias`): `inscricoes CANCELADA` + `ingressos CANCELADO` + libera vaga (ADR-T07). Se PAGO e dentro do prazo -> reembolso individual (`CANCELAMENTO_PARTICIPANTE`) via AMQP. Fora do prazo -> **422** (PO-D2, bloqueia).
- **US-024** — participante elegivel (ingresso `UTILIZADO` **ou** inscricao `ATIVA` em evento `REALIZADO`, PO-D1) avalia: nota 1-5 + comentario; `UNIQUE(evento,usuario)` -> 409 na 2a; nao-elegivel -> 403; nota fora de 1-5 -> 400.
- **US-025** — `GET /events/{id}` passa a incluir `reputacao{media, total}` (AVG+COUNT agregados, sem N+1).

## Servico(s) afetado(s)
| Servico | Papel na 5B | Mudanca principal |
|---|---|---|
| **ticket-service** | Check-in (US-034), cancelamento+gatilho de reembolso (US-035) | `Checkin` entity + repo; `CheckinService`; `Ingresso.realizarCheckin()` (transicao condicional ATIVO->UTILIZADO que **lanca** 409 se ja UTILIZADO — distinta de `utilizar()`); `CancelamentoInscricaoService` (cancela propria + libera vaga + publica `inscricao.cancelada` se PAGO); `InscricaoCanceladaPublisher` (afterCommit); `EventResumo`/`EventClient` passam a ler `dataInicio`+`prazoReembolsoDias` (politica de prazo). Endpoints `POST /tickets/checkin` e `DELETE /tickets/inscricoes/{id}`. **Novo controller interno** `/internal/tickets/participou` (callee da elegibilidade). RabbitConfig += publisher (so produz `inscricao.cancelada`). |
| **event-service** | Avaliacao (US-024) + reputacao (US-025) | `Avaliacao` entity + repo; `AvaliacaoService` (checa elegibilidade via **TicketClient** interno); `AvaliacaoController` `POST /events/{id}/avaliacoes`; `EventoResponse += reputacao{media,total}` calculado por query agregada; `TicketClient` (RestClient outbound -> ticket `/internal/tickets/participou`); `EventoInternoResponse += dataInicio, prazoReembolsoDias` (para o ticket checar prazo). **Sem mensageria nova** (event nao consome nada da 5B). |
| **payment-service** | Consumidor do reembolso individual (US-035 pago) | **Nova fila** `inscricao.cancelada` + DLQ; `InscricaoCanceladaListener` idempotente (reusa `findByInscricaoIdForUpdate` + `reembolsar()` + `Reembolso.criar(...,'CANCELAMENTO_PARTICIPANTE')`). **Sem migration** (`reembolsos.motivo` CHECK ja aceita `CANCELAMENTO_PARTICIPANTE`; `processed_events` ja existe). |
| **infra** | Topologia | **`inscricao.cancelada`** + `inscricao.cancelada.dlq` + bindings em `definitions.json`. Consumidor unico = payment -> 1 fila basta (sem fan-out). |
| **frontend** | (fora desta trilha de arquitetura) | Telas scanner QR (Marina), cancelar inscricao com prazo (Bruno), avaliar + ver reputacao. Spec de UI fica no handoff; nao detalhada aqui. |

## Decisoes que o Arquiteto fecha (ADR — ver decisions.md ADR-T14/T15/T16)

### 1. Check-in: auth = PROMOTOR + ownership do evento (US-034) — ADR-T14
- O endpoint `POST /tickets/checkin` recebe `{codigo_unico}` e o header `X-User-Id`/`X-User-Papel` (gateway). Exige papel `PROMOTOR` (403 senao).
- **Ownership:** o ticket-service faz lookup `Ingresso by codigo_unico` -> obtem `inscricao_id` -> `Inscricao.evento_id`. Para saber o **dono do evento**, chama `EventClient.getEvento(eventoId)` (canal interno ja existente, ADR-T08) e compara `evento.promotorId() == userId`. Diferente -> **403** (`CHECKIN_EVENTO_ALHEIO`). `EventResumo.promotorId()` **ja existe** — nenhum campo novo necessario para a ownership.
- **Por que double-check (papel + ownership), nao so papel:** R4 da spec — um promotor A nao pode validar ingressos do evento de Marina. O papel sozinho nao basta (qualquer PROMOTOR validaria qualquer QR).
- **Lookup, nao cripto:** consistente com ADR-T09 (UUID v4, check-in = lookup server-side `WHERE codigo_unico=? AND status=ATIVO`).

### 2. Reembolso individual (US-035 pago): gatilho AMQP `inscricao.cancelada` — ADR-T15
- **Escolha: evento AMQP `inscricao.cancelada`** publicado pelo ticket em `afterCommit` -> payment consome (idempotente) -> reembolsa **o pagamento daquela inscricao**.
- **Justificativa vs. alternativa sincrona (token interno ticket->payment):**
  1. **Consistencia com a 5A.** O reembolso em massa ja e orientado a evento (`evento.cancelado`); o individual seguir o mesmo padrao mantem uma unica forma de estornar (mesmo `reembolsar()`+`Reembolso.criar`), idempotente via `processed_events`. Uma chamada sincrona introduziria um **2o mecanismo** de estorno (acoplamento ticket->payment no caminho quente do cancelamento, timeout, retry, compensacao).
  2. **Desacoplamento + resiliencia.** Se o payment estiver fora do ar, a chamada sincrona faria o cancelamento falhar OU exigiria saga de compensacao. Com AMQP, o cancelamento commita localmente (inscricao/ingresso/vaga) e o reembolso e processado quando o payment consumir (at-least-once + DLQ). O criterio do PO ("Bruno ve o reembolso no extrato") nao exige sincronia — o extrato e eventualmente consistente, como na 5A.
  3. **Idempotencia gratuita.** `processed_events(eventId)` ja resolve reentrega; uma chamada sincrona precisaria de chave de idempotencia propria.
- **`inscricaoId` -> pagamento:** `pagamentos.inscricao_id` e **UNIQUE** -> `findByInscricaoIdForUpdate(inscricaoId)` carrega exatamente 1 pagamento com lock pessimista. Reembolso so se `CONFIRMADO` (transicao condicional `reembolsar()` -> no-op se PENDENTE/ja REEMBOLSADO).
- **Payload:** `inscricao.cancelada { eventId(UUID), inscricaoId, usuarioId, eventoId, ocorridoEm }`. `eventId` UUID gerado na origem (ticket) = chave de idempotencia (ADR-T11).
- **Topologia:** consumidor unico (payment) -> **1 fila** `inscricao.cancelada` (sem fan-out, diferente de `evento.cancelado`). Nova fila + DLQ no `definitions.json` + nas RabbitConfig do payment (consome) e do ticket (so produz; declara a exchange — ja declara).

### 3. Politica de prazo (US-035): checada no TICKET, com dados do evento — ADR-T15
- **Onde:** no ticket-service, ANTES de cancelar, para eventos **PAGO**. Para **GRATUITO** nao ha prazo (sempre pode cancelar; sem reembolso — criterio US-035.1).
- **Dados necessarios:** `prazo_reembolso_dias` e `data_inicio` do evento. **Faltam no `EventResumo`/`EventoInternoResponse`** (hoje so expoem preco/promotorId/vagas/capacidade/tipo/status). **Expor ambos** (ADR-T15): `EventoInternoResponse += dataInicio (OffsetDateTime), prazoReembolsoDias (Integer)` e espelhar em `EventResumo`. Sem migration (colunas ja existem em `eventos`).
- **Regra:** dentro do prazo sse `now <= data_inicio - prazo_reembolso_dias` (limite = inicio do evento menos a janela). Fora -> **422** `PRAZO_CANCELAMENTO_ENCERRADO` (PO-D2: bloqueia, inscricao permanece ATIVA, nenhum reembolso). Borda: `data_inicio` ja passou -> claramente fora do prazo -> 422.
- **Por que no ticket (e nao no event):** o ticket ja orquestra o cancelamento (inscricao/ingresso/vaga) e ja chama o event via `EventClient`; concentrar a decisao evita um round-trip extra e mantem o event-service sem conhecimento de inscricoes.

### 4. Elegibilidade de avaliacao cross-service (US-024): event chama o ticket — ADR-T16
- **Escolha: event-service chama o ticket-service** por canal interno `GET /internal/tickets/participou?usuarioId=&eventoId=` -> `{participou: boolean}`, exige `X-Internal-Token` (ADR-T08).
- **Justificativa vs. "ticket expoe e event consulta de outra forma":** a avaliacao e do dominio do event-service (tabela `avaliacoes` ja vive la, `UNIQUE(evento,usuario)`). O event-service e quem decide 403/409/201, entao ele deve **perguntar** ao ticket "esse usuario participou?" — o ticket e o dono da verdade de inscricoes/check-in. O inverso (ticket consultar avaliacoes) inverteria a responsabilidade. Espelha a direcao ja existente ticket->event (`EventClient`), agora event->ticket (`TicketClient`), reusando o padrao `/internal/**` + `X-Internal-Token`.
- **Regra de elegibilidade (PO-D1), avaliada NO TICKET:** `participou = true` sse existe, para `(usuarioId, eventoId)`:
  - um `Ingresso` com `status=UTILIZADO` (fez check-in), **OU**
  - uma `Inscricao` com `status=ATIVA` **E** o evento esta `REALIZADO`.
  - A condicao "evento REALIZADO" e do event-service. **Decisao:** o `event-service` ja sabe o status do evento (ele e o caller); entao ele so chama o ticket quando o evento esta `REALIZADO` (pre-filtro local barato) **e** passa a pergunta de participacao. O ticket responde apenas a parte que e dele: "tem ingresso UTILIZADO OU inscricao ATIVA para esse usuario+evento?". Assim a regra completa = `evento.status==REALIZADO (event) AND ticket.participou (ticket)`. Inscricao CANCELADA/EXPIRADA -> nao conta (o ticket so olha UTILIZADO/ATIVA).
  - Edge: ingresso `UTILIZADO` mas evento ainda nao REALIZADO -> o event pre-filtra `REALIZADO`, entao avaliacao so abre apos o promotor encerrar. Coerente com "avaliar evento que ja aconteceu".

### 5. Reputacao (US-025): AVG+COUNT agregado — sem N+1
- Query agregada unica no `AvaliacaoRepository`: `SELECT new ...(AVG(a.nota), COUNT(a)) FROM Avaliacao a WHERE a.eventoId=:id`. Sem media -> `media=null, total=0` (criterio US-025.1). `media` arredondada na borda (1 casa) ou exposta como `Double` cru (decisao: expor `Double` e deixar o front formatar; `total` = `long`). Usa `idx_avaliacoes_evento` (V1).
- Exposta em `EventoResponse.reputacao` (record aninhado `ReputacaoResponse(Double media, long total)`). Calculada no `EventService.detalhe(...)` (1 query agregada adicional, O(1)). Sem cache (criterio US-025.2: "sem cache obsoleto").

## Eventos de dominio (AMQP) — detalhe em api-contracts.md
| Routing key | Produtor | Consumidor | Efeito |
|---|---|---|---|
| **`inscricao.cancelada`** *(NOVA)* | ticket | payment | reembolso individual do pagamento daquela inscricao: `CONFIRMADO -> REEMBOLSADO` + `reembolsos(CANCELAMENTO_PARTICIPANTE)`. Idempotente via `processed_events`. |

> So payment consome -> **1 fila** `inscricao.cancelada` (+DLQ). event-service nao participa do AMQP da 5B.

## Fluxos / sagas

### CHECK-IN (US-034)
```
Marina (PROMOTOR) -> POST /api/tickets/checkin { codigo_unico }
  ticket: requireUserId + requirePromotor(papel)
          Ingresso ing = findByCodigoUnico(codigo)   -> null? 404 INGRESSO_NAO_ENCONTRADO
          ing.status == CANCELADO?                    -> 404 INGRESSO_NAO_ENCONTRADO (cancelada/invalida)
          Inscricao ins = findById(ing.inscricaoId)
          EventResumo ev = eventClient.getEvento(ins.eventoId)
          ev.promotorId != userId?                    -> 403 CHECKIN_EVENTO_ALHEIO
          [tx]:
             ing.realizarCheckin()   // ATIVO->UTILIZADO; se ja UTILIZADO -> lanca BusinessException("INGRESSO_JA_UTILIZADO",409)
             checkinRepository.saveAndFlush(Checkin.de(ing.id))  // UNIQUE(ingresso_id) -> 2o simultaneo: DataIntegrityViolation -> 409
          200 CheckinResponse{ ingressoId, status=UTILIZADO, realizadoEm }
```
Concorrencia (duplo check-in do MESMO QR): a barreira atomica e o **`UNIQUE(ingresso_id)` em `checkins`** — 2 devices simultaneos inserem o mesmo `checkin`, 1 vence e o outro colide (`DataIntegrityViolationException -> 409`). `Ingresso.realizarCheckin()` cobre o caminho sequencial (409 se ja UTILIZADO). Ver Estrategias. (Nota CR-5B-02: a mutacao de `ingressos.status` usa dirty-check do JPA, sem `WHERE status='ATIVO'`; isso e suficiente para o duplo check-in por causa do UNIQUE, mas ver a linha "Cancelar e check-in simultaneos" para a corrida cross-actor.)

### CANCELAMENTO + reembolso individual (US-035 + US-042 individual)
```
Bruno -> DELETE /api/tickets/inscricoes/{id}   (X-User-Id)
  ticket:
    Inscricao ins = findById(id)               -> null? 404
    ins.usuarioId != userId?                   -> 403 CANCELAMENTO_DE_OUTRO (nao cancela alheia)
    ins.status nao-cancelavel (ja CANCELADA/EXPIRADA)? -> 409 INSCRICAO_JA_CANCELADA
    EventResumo ev = eventClient.getEvento(ins.eventoId)   // traz tipo, dataInicio, prazoReembolsoDias
    SE ev.tipo == PAGO:
        dentroDoPrazo = now <= ev.dataInicio - ev.prazoReembolsoDias(dias)
        SE !dentroDoPrazo: -> 422 PRAZO_CANCELAMENTO_ENCERRADO   (PO-D2; inscricao permanece ATIVA)
    [tx local]:
        ins.cancelarPorParticipante()   // ATIVA|PENDENTE_PAGAMENTO -> CANCELADA (lanca se invalido p/ controle? ver nota)
        ingresso (se houver) -> CANCELADO   (ATIVO->CANCELADO; UTILIZADO preservado)
        afterCommit:
           eventClient.liberarVaga(ins.eventoId)     // ADR-T07 (so PUBLICADO; no-op senao)
           SE ev.tipo == PAGO E dentro do prazo:
               inscricaoCanceladaPublisher.publicar( inscricao.cancelada{ eventId(UUID), inscricaoId, usuarioId, eventoId } )
    200/204
  ----------------------------------------------------------------
  payment @RabbitListener(inscricao.cancelada): [tx]
     INSERT processed_events(eventId)   -> PK colide? reentrega -> ACK no-op
     Pagamento p = findByInscricaoIdForUpdate(inscricaoId)
        ausente? -> ACK no-op (evento gratuito nunca publica; defesa)
     SE p.reembolsar() (CONFIRMADO->REEMBOLSADO):
        Reembolso.criar(p.id, p.usuarioId, p.valorBruto, 'CANCELAMENTO_PARTICIPANTE')
        save
     (p nao-CONFIRMADO -> no-op, ACK; nunca poison message — CR-S4-01)
```
> **Ordem liberar-vaga vs. publicar:** ambos em afterCommit. `liberarVaga` e a compensacao ADR-T07 (idempotente no teto). Publicar `inscricao.cancelada` so se PAGO+dentro-do-prazo (gratuito nunca tem pagamento; fora-do-prazo nem chega aqui — barrado em 422 antes da tx).

> **Nota de transicao no cancelamento voluntario:** distinta do `cancelarPorEvento()` da 5A (que e no-op idempotente para consumidor AMQP). Aqui o cancelamento e **sincrono iniciado pelo usuario**: 2o cancelamento simultaneo da mesma inscricao deve dar **409** (criterio US-035.5). Solucao: transicao condicional via `UPDATE inscricoes SET status=CANCELADA WHERE id=? AND status IN (ATIVA,PENDENTE_PAGAMENTO)` retornando rowsAffected; 0 -> 409 `INSCRICAO_JA_CANCELADA`. Serializa concorrentes pelo row lock. Ver Estrategias.

### AVALIACAO (US-024) + REPUTACAO (US-025)
```
Bruno -> POST /api/events/{id}/avaliacoes { nota, comentario }   (X-User-Id)
  event:
    evento = findById(id) -> 404 senao
    SE evento.status != REALIZADO: -> 403 AVALIACAO_NAO_ELEGIVEL  (pre-filtro local)
    participou = ticketClient.participou(userId, eventoId)   // GET /internal/tickets/participou
    SE !participou: -> 403 AVALIACAO_NAO_ELEGIVEL
    [tx]:
       avaliacaoRepository.saveAndFlush(Avaliacao.criar(eventoId, userId, nota, comentario))
       // UNIQUE(evento_id,usuario_id) -> 2a -> DataIntegrityViolation -> 409 AVALIACAO_DUPLICADA
    201 AvaliacaoResponse
  (nota fora de 1-5 -> 400 via @Min/@Max na borda, antes da regra)

Reputacao (US-025): GET /api/events/{id}
  event.detalhe(userId,id) -> Evento + reputacaoRepository.agregar(id) = (AVG(nota), COUNT)
  EventoResponse.reputacao = { media: Double|null, total: long }
```

## Estrategias criticas

### Concorrencia
| Cenario | Estrategia |
|---|---|
| Duplo check-in do mesmo QR (US-034.6) | **`UNIQUE(ingresso_id)` em `checkins`** (V1, ja existe) + transicao `Ingresso ATIVO->UTILIZADO` que **lanca 409 se ja UTILIZADO**. 2 devices simultaneos: 1 vence (insere checkin + UTILIZADO), o outro colide no `UNIQUE` (`DataIntegrityViolationException -> 409`) ou ve `INGRESSO_JA_UTILIZADO`. O insert do `checkins` e a barreira atomica final. |
| Cancelar e check-in simultaneos do mesmo ingresso | Ambos mutam `ingressos.status` a partir de `ATIVO` (check-in `->UTILIZADO`; cancelamento `->CANCELADO`) via **dirty-check do JPA, sem guard `WHERE status='ATIVO'`** → **last-writer-wins** no `ingressos.status`, sem 409 ao perdedor (CR-5B-02). **Isto NAO quebra invariante critica:** o duplo check-in continua barrado pelo `UNIQUE(ingresso_id)`, e o reembolso unico depende de `pagamentos.status` (transicao condicional sob lock), nao de `ingressos.status`. O residuo e um estado terminal do ingresso inconsistente numa corrida entre atores distintos (promotor na porta vs. participante cancelando no mesmo instante) — janela estreita, impacto de auditoria. **Hardening (trocar por `UPDATE ... WHERE status='ATIVO'` retornando rowsAffected → 409 ao perdedor) previsto na 5C** (US de observabilidade/hardening); ver `code-review.md` CR-5B-02 e `bugs.md`. |
| 2 cancelamentos simultaneos da mesma inscricao (US-035.5) | `UPDATE inscricoes SET status=CANCELADA WHERE id=? AND status IN (ATIVA,PENDENTE_PAGAMENTO)` -> rowsAffected; 1=sucesso, 0=409 `INSCRICAO_JA_CANCELADA`. Row lock serializa. Vaga liberada 1x (so o vencedor chama liberarVaga). |
| Dupla avaliacao (US-024.2) | `UNIQUE(evento_id,usuario_id)` (V1) + `DataIntegrityViolationException -> 409 AVALIACAO_DUPLICADA`. |
| `inscricao.cancelada` reentregue | `processed_events(eventId)` PK no payment -> 2a entrega colide -> ACK no-op. Reembolso aplicado 1x. |
| Reembolso individual vs. reembolso em massa no mesmo pagamento (corrida rara: participante cancela enquanto promotor cancela evento) | Ambos chamam `reembolsar()` condicional (`status='CONFIRMADO'`) sob lock pessimista (`findByInscricaoIdForUpdate` vs. `findConfirmadosDoEventoForUpdate`). Row lock serializa -> 1 vence, o outro no-op. UNIQUE parcial `uk_reembolso_evento_cancelado` cobre so EVENTO_CANCELADO; o individual usa motivo distinto -> nao colidem no indice, mas a transicao condicional garante 1 unico estorno. |

### Idempotencia
- **payment** reusa `processed_events` (V2, existe) para `inscricao.cancelada` (nova routing key na coluna). `eventId` UUID gerado no `InscricaoCanceladaPublisher` (origem).
- **Defesa em profundidade:** o reembolso so e criado se `reembolsar()` transicionou (CONFIRMADO->REEMBOLSADO); reentrega encontra REEMBOLSADO -> no-op. (Opcional, **nao incluido**: UNIQUE parcial `WHERE motivo='CANCELAMENTO_PARTICIPANTE'` — o `processed_events` + transicao condicional ja bastam; YAGNI salvo o PO pedir simetria com a 5A.)
- **check-in/avaliacao** sao REST sincronos: idempotencia natural pelas constraints `UNIQUE(ingresso_id)` e `UNIQUE(evento,usuario)`.

### Performance / indices
- Check-in: lookup `Ingresso WHERE codigo_unico=?` usa o `UNIQUE(codigo_unico)` (V1) — O(1). **Sem indice novo.**
- Reputacao: `AVG/COUNT WHERE evento_id=?` usa `idx_avaliacoes_evento` (V1). 1 query agregada, O(1) round-trips, sem N+1.
- Elegibilidade (ticket): `EXISTS ingresso UTILIZADO de inscricao do usuario+evento` e `EXISTS inscricao ATIVA usuario+evento` — usam `idx_inscricoes_usuario`/`idx_inscricoes_evento` (V1). Defini query EXISTS unica para evitar 2 round-trips (ver data-model).
- Cancelamento: `findById(inscricao)` por PK; `UPDATE ... WHERE id=? AND status IN(...)` por PK.

### Seguranca / auth
- `POST /tickets/checkin`: `X-User-Id` + `X-User-Papel==PROMOTOR` (403) + ownership do evento via `EventClient` (403). Sem divida nova (papel ja no header desde 5A; `X-User-Papel` injetado pelo gateway).
- `DELETE /tickets/inscricoes/{id}`: `X-User-Id`; ownership = `inscricao.usuarioId==userId` (403 senao). Nao precisa de papel (qualquer participante cancela a propria).
- `POST /events/{id}/avaliacoes`: `X-User-Id`; sem exigencia de papel (qualquer participante elegivel). Admin/promotor nao-participante -> 403 pela regra de elegibilidade (US-024.5).
- `GET /events/{id}` (reputacao): inalterado — qualquer autenticado (US-025.3).
- **Canais internos:** `GET /internal/tickets/participou` exige `X-Internal-Token` (403 senao); o gateway nao roteia `/api/internal/**` (404 externo) — ADR-T08. `EventClient.getEvento` ja injeta o token.

## Componentes backend (delta)
- **ticket-service:**
  - `domain/Checkin` (entity, `@Table(checkins)`, `UNIQUE ingresso_id`), `domain/StatusIngresso` (inalterado), `Ingresso.realizarCheckin()` (novo — lanca 409 em ja-UTILIZADO; distinto do `utilizar()` idempotente da 5A usado em fixtures).
  - `repository/CheckinRepository`, `IngressoRepository += findByCodigoUnico`, `InscricaoRepository += cancelarPorParticipante(UPDATE condicional)` + `findById`.
  - `service/CheckinService`, `service/CancelamentoInscricaoService`.
  - `controller/CheckinController` (`POST /tickets/checkin`), `DELETE` em `TicketController` ou `CancelamentoController` (`DELETE /tickets/inscricoes/{id}`).
  - `controller/InternalTicketController` (`GET /internal/tickets/participou`, valida `X-Internal-Token`, espelha `InternalEventController` do event).
  - `messaging/InscricaoCanceladaEvent` (record), `InscricaoCanceladaPublisher` (afterCommit), RabbitConfig += exchange ja declarada (so produz; opcional declarar a fila p/ testes).
  - `client/EventResumo += dataInicio, prazoReembolsoDias`.
- **event-service:**
  - `domain/Avaliacao` (entity), `repository/AvaliacaoRepository` (+ agregacao reputacao).
  - `service/AvaliacaoService`, `controller/AvaliacaoController` (`POST /events/{id}/avaliacoes`).
  - `client/TicketClient` + `TicketClientConfig` (RestClient outbound, `X-Internal-Token`, `app.ticket-service.url`).
  - `dto/AvaliacaoRequest`(@NotNull nota @Min(1)@Max(5), comentario @Size), `AvaliacaoResponse`, `ReputacaoResponse(Double media, long total)`.
  - `EventoResponse += reputacao`; `EventoInternoResponse += dataInicio, prazoReembolsoDias`; `EventService.detalhe` injeta reputacao.
- **payment-service:**
  - `messaging/InscricaoCanceladaEvent` (record), `InscricaoCanceladaListener` (idempotente, reusa `reembolsar()`/`Reembolso.criar`), RabbitConfig += fila `inscricao.cancelada`+DLQ+binding.
  - `PagamentoService += reembolsarPorInscricao(inscricaoId, motivo)` **ou** logica inline no listener (preferir metodo no service para testabilidade unitaria).
- **Reuso:** `processed_events` (payment/ticket), `GlobalExceptionHandler`, padrao `afterCommit`/`TransactionSynchronization`, `TestcontainersBase` singleton (ticket/event/payment), `EventClient`/`InternalEventController` (gabarito do canal interno), `RabbitConfig` delegado ao autoconfigure.

## Migrations (delta)
| Servico | Migration | Conteudo |
|---|---|---|
| **ticket** | `V3__checkin.sql` | `Checkin` entity precisa de mapear a tabela `checkins` (**ja existe em V1** com `UNIQUE(ingresso_id)`). **Decisao:** sem DDL novo se a V1 ja cria `checkins`. **POReM** o `valido BOOLEAN` da V1 e dispensavel; a entity mapeia `id, ingresso_id, realizado_em, valido(default true)`. **Conclusao: SEM migration nova** (tabela `checkins` ja existe; entity nova mapeia o existente). |
| **event** | — | `avaliacoes` ja existe (V1, `UNIQUE(evento,usuario)`, `CHECK nota 1-5`). `dataInicio`/`prazoReembolsoDias` ja sao colunas. **SEM migration.** |
| **payment** | — | `reembolsos.motivo` CHECK ja aceita `CANCELAMENTO_PARTICIPANTE` (V1). `processed_events` ja existe (V2). **SEM migration.** |
| **infra** | `definitions.json` | += fila `inscricao.cancelada` + `inscricao.cancelada.dlq` + bindings (exchange->fila rk `inscricao.cancelada`; dlx->dlq). |

> **Resumo: ZERO migrations Flyway nas 4 services.** Toda a 5B reusa schema existente. Unica mudanca de infra: a fila nova no `definitions.json` + nas RabbitConfig (payment declara/consome; ticket so produz).

## Riscos tecnicos
| Risco | Prob | Impacto | Mitigacao |
|---|---|---|---|
| Check-in nao distingue "ja utilizado" (409) de cancelamento idempotente (5A) | Media | 2o check-in nao retorna 409 | `Ingresso.realizarCheckin()` **lanca** em ja-UTILIZADO (novo metodo), separado do `utilizar()` no-op usado por fixtures da 5A. O `UNIQUE(ingresso_id)` em `checkins` e a barreira atomica final no caso concorrente. |
| Prazo: `EventResumo` nao expoe `dataInicio`/`prazoReembolsoDias` | Alta (e fato) | ticket nao consegue checar prazo | Expor ambos em `EventoInternoResponse` + `EventResumo` (ADR-T15). Sem migration (colunas existem). Teste do canal interno valida os campos. |
| Reembolso individual reentregue ou em corrida com massa | Media | reembolso duplo | `processed_events` + `reembolsar()` condicional + lock pessimista; corrida com massa -> 1 vence (testado). |
| Elegibilidade: event chama ticket com token errado -> 403 vira 500/avaliacao trava | Media | participante elegivel barrado | `TicketClient` traduz 403 interno em erro logado (mis-config INTERNAL_TOKEN) -> falha **fechada** (nega avaliacao com 503/403 tipado, nunca 500). Espelha `EventClient.getEvento`. |
| event-service ganha **outbound** RestClient pela 1a vez (so tinha inbound interno) | Media | config/url errada | `TicketClientConfig` espelha EXATAMENTE `EventClientConfig` do ticket (timeouts 2s/3s, `defaultHeader X-Internal-Token`, baseUrl `app.ticket-service.url`). Teste com ticket mockado/WireMock. |
| Testcontainers so no CI (Windows local pula) | Alta (ambiente) | saga do reembolso individual so verde no CI | Padrao S4/5A: `TestcontainersBase` singleton + `@BeforeEach` purga `inscricao.cancelada`. Local roda unit/H2; CI roda a saga. |

## Definition of Done tecnico
- [ ] **Sem migration Flyway** (validar que `checkins`/`avaliacoes`/`reembolsos.motivo`/`processed_events` existentes cobrem 5B).
- [ ] `Ingresso.realizarCheckin()` lanca 409 em ja-UTILIZADO; `Checkin` entity + `UNIQUE(ingresso_id)` barram duplo (Testcontainers concorrencia).
- [ ] `POST /tickets/checkin`: PROMOTOR + ownership (403 alheio/papel), 404 inexistente/cancelado, 409 duplo.
- [ ] `DELETE /tickets/inscricoes/{id}`: gratuito libera vaga; pago dentro do prazo publica `inscricao.cancelada`; fora do prazo -> 422; alheia -> 403; 2o -> 409.
- [ ] payment consome `inscricao.cancelada` idempotente -> `REEMBOLSADO` + `reembolsos(CANCELAMENTO_PARTICIPANTE)`; reentrega 1x; corrida c/ massa -> 1 vencedor.
- [ ] `POST /events/{id}/avaliacoes`: elegivel 201, 2a 409, nao-elegivel 403, nota fora 400.
- [ ] `GET /events/{id}` inclui `reputacao{media,total}` (AVG/COUNT, sem N+1; null/0 sem avaliacoes).
- [ ] `GET /internal/tickets/participou`: 403 sem token; boolean correto com token.
- [ ] `EventoInternoResponse`/`EventResumo` expoem `dataInicio`+`prazoReembolsoDias`.
- [ ] cobertura >=90% nos services de check-in/cancelamento/reembolso/avaliacao; sem N+1.
- [ ] Testcontainers PG+Rabbit verdes no CI. OpenAPI/Swagger atualizado. `./mvnw verify` verde.

## Para o PO validar (Fase 3)
1. **Gatilho do reembolso individual = AMQP `inscricao.cancelada`** (assincrono, eventual, como a 5A) — confirmar que "Bruno ve o reembolso no extrato" eventualmente consistente e aceitavel (igual a 5A). Alternativa sincrona registrada e rejeitada por acoplamento.
2. **Politica de prazo checada no ticket** com `dataInicio - prazoReembolsoDias`; fora -> **422** sem reembolso, inscricao permanece ATIVA (PO-D2). Confirmar a formula do limite (inicio do evento menos a janela em dias).
3. **Elegibilidade = evento REALIZADO (event) AND (ingresso UTILIZADO OU inscricao ATIVA) (ticket)** via canal interno (PO-D1). Confirmar que ingresso UTILIZADO so habilita avaliacao **apos** o promotor encerrar o evento (pre-filtro REALIZADO no event).
4. **Reputacao** = `media` (Double, null sem avaliacoes) + `total` (long), sem cache. Confirmar formato (front formata as casas decimais).
5. **Zero migrations** — confirmar que nao ha expectativa de coluna nova (ex.: `inscricoes.cancelada_em`/`motivo`). Se auditoria de cancelamento for desejada, seria uma migration opcional fora do minimo (nao incluida).
