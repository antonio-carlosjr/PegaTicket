# Sprint 5 — Trilha 5B (Experiencia) — Contratos de API

> REST (via gateway) + payloads AMQP. Erros tipados traduzidos pelo `GlobalExceptionHandler` em `ErrorResponse{timestamp,status,error,message,path}`.
> **Convencao de rota:** rota interna do servico sem `/api`; via gateway com `/api` + `StripPrefix=1`. Gateway roteia **apenas** `/api/events/**` (event), `/api/tickets/**` (ticket), `/api/payments/**` (payment).
> **IMPORTANTE — reconciliacao de prefixos:** o PO/spec escreveram `/api/ingressos/checkin`, `/api/inscricoes/{id}`, `/api/eventos/{id}/avaliacoes` de forma informal. Esses prefixos **NAO sao roteados** pelo gateway. Os contratos REAIS desta trilha usam os prefixos roteados existentes (sem tocar o gateway, fora do escopo 5B):
> - check-in -> `POST /api/tickets/checkin`
> - cancelar inscricao -> `DELETE /api/tickets/inscricoes/{id}`
> - avaliar -> `POST /api/events/{id}/avaliacoes`
> - detalhe c/ reputacao -> `GET /api/events/{id}` (ja roteado)

---

## 1. REST — ticket-service

### 1.1 POST /tickets/checkin  (via gateway: POST /api/tickets/checkin)   [US-034]
Promotor dono valida o QR na porta: `ingressos ATIVO -> UTILIZADO` + cria `checkins`.

**Auth:** `X-User-Id` (gateway) + `X-User-Papel == PROMOTOR` + **ownership** do evento (`evento.promotorId == userId`, descoberto via `EventClient.getEvento`).

**Request (record + Bean Validation):**
```java
record CheckinRequest(@NotBlank String codigoUnico) {}
```
```json
{ "codigoUnico": "c0ffee00-1111-2222-3333-444455556666" }
```

**Response 200** (`CheckinResponse`):
```java
record CheckinResponse(Long ingressoId, Long inscricaoId, String status, OffsetDateTime realizadoEm) {}
```
```json
{ "ingressoId": 42, "inscricaoId": 17, "status": "UTILIZADO", "realizadoEm": "2026-09-01T20:05:00-03:00" }
```

**Erros (ErrorResponse tipado):**
| HTTP | code (message) | Quando |
|---|---|---|
| 400 | (Bean Validation) | `codigoUnico` ausente/vazio |
| 401 | `Autenticacao obrigatoria.` | `X-User-Id` ausente |
| 403 | `Acesso restrito a promotores.` | `X-User-Papel != PROMOTOR` (US-034.5) |
| 403 | `CHECKIN_EVENTO_ALHEIO` | promotor nao e dono do evento do ingresso (US-034.3) |
| 404 | `INGRESSO_NAO_ENCONTRADO` | `codigo_unico` inexistente **ou** ingresso CANCELADO/inscricao cancelada (US-034.4) |
| 409 | `INGRESSO_JA_UTILIZADO` | 2a leitura do mesmo QR (US-034.2 / .6) |

> **Concorrencia (US-034.6):** 2 devices simultaneos -> 1 grava `checkins` (`UNIQUE(ingresso_id)`) + `UTILIZADO`; o outro colide -> 409. Sem duplicata.

---

### 1.2 DELETE /tickets/inscricoes/{id}  (via gateway: DELETE /api/tickets/inscricoes/{id})   [US-035]
Participante cancela a propria inscricao dentro da politica; libera vaga; se pago+dentro do prazo, dispara reembolso individual (AMQP).

**Auth:** `X-User-Id`; ownership = `inscricao.usuarioId == userId`.

**Request:** sem body.

**Response 200** (`CancelamentoResponse`) — explicita o efeito p/ o front:
```java
record CancelamentoResponse(Long inscricaoId, String status, boolean reembolsoIniciado) {}
```
```json
{ "inscricaoId": 17, "status": "CANCELADA", "reembolsoIniciado": true }
```
> `reembolsoIniciado=true` apenas para evento PAGO dentro do prazo (publicou `inscricao.cancelada`). Gratuito ou sem pagamento -> `false`.

**Erros (ErrorResponse tipado):**
| HTTP | code (message) | Quando |
|---|---|---|
| 401 | `Autenticacao obrigatoria.` | `X-User-Id` ausente |
| 403 | `CANCELAMENTO_DE_OUTRO` | inscricao de outro participante (US-035.4) |
| 404 | `INSCRICAO_NAO_ENCONTRADA` | id inexistente |
| 409 | `INSCRICAO_JA_CANCELADA` | inscricao ja CANCELADA/EXPIRADA; ou 2o cancelamento concorrente (US-035.5) |
| 422 | `PRAZO_CANCELAMENTO_ENCERRADO` | evento PAGO fora do prazo `prazo_reembolso_dias` (PO-D2 / US-035.3); inscricao permanece ATIVA |
| 503 | `EVENTO_INDISPONIVEL` | `EventClient` nao responde (nao vira 500) |

**Evento emitido:** `inscricao.cancelada` (afterCommit, **somente** se PAGO + dentro do prazo). Ver §3.1.

---

## 2. REST — event-service

### 2.1 POST /events/{id}/avaliacoes  (via gateway: POST /api/events/{id}/avaliacoes)   [US-024]
Participante elegivel avalia o evento (nota 1-5 + comentario opcional).

**Auth:** `X-User-Id`. Sem exigencia de papel; a elegibilidade (PO-D1) e checada via canal interno ao ticket.

**Request (record + Bean Validation):**
```java
record AvaliacaoRequest(
    @NotNull @Min(1) @Max(5) Integer nota,
    @Size(max = 2000) String comentario
) {}
```
```json
{ "nota": 4, "comentario": "Show incrivel, organizacao impecavel." }
```

**Response 201** (`AvaliacaoResponse`):
```java
record AvaliacaoResponse(Long id, Long eventoId, Long usuarioId, Integer nota,
                         String comentario, OffsetDateTime avaliadoEm) {}
```

**Erros (ErrorResponse tipado):**
| HTTP | code (message) | Quando |
|---|---|---|
| 400 | (Bean Validation) | nota null/fora de 1-5 (US-024.4) |
| 401 | `Autenticacao obrigatoria.` | `X-User-Id` ausente |
| 403 | `AVALIACAO_NAO_ELEGIVEL` | evento nao REALIZADO, sem participacao, inscricao cancelada, ou admin/promotor nao-participante (US-024.3 / .5) |
| 404 | `Evento nao encontrado.` | evento inexistente |
| 409 | `AVALIACAO_DUPLICADA` | 2a avaliacao do mesmo usuario no evento — `UNIQUE(evento,usuario)` (US-024.2) |
| 503 | `TICKET_INDISPONIVEL` | `TicketClient` nao responde (falha fechada; nunca 500) |

---

### 2.2 GET /events/{id}  (via gateway: GET /api/events/{id})   [US-025]  — ALTERADO (additive)
Detalhe do evento passa a incluir `reputacao`. Contrato existente inalterado; **campo novo aditivo**.

**Auth:** `X-User-Id` (qualquer autenticado — US-025.3). Inalterado.

**Response 200** (`EventoResponse` += `reputacao`):
```java
record ReputacaoResponse(Double media, long total) {}   // media null quando total=0

record EventoResponse(
    Long id, String titulo, String descricao,
    OffsetDateTime dataInicio, OffsetDateTime dataFim, String local,
    TipoEvento tipo, StatusEvento status,
    Integer capacidade, Integer vagasDisponiveis,
    BigDecimal preco, Integer prazoReembolsoDias, String imagemUrl,
    Long promotorId, OffsetDateTime criadoEm, OffsetDateTime atualizadoEm,
    ReputacaoResponse reputacao        // NOVO (US-025)
) {}
```
```json
{ "id": 10, "status": "REALIZADO", "...": "...", "reputacao": { "media": 4.2, "total": 37 } }
```
Sem avaliacoes: `"reputacao": { "media": null, "total": 0 }` (US-025.1). Atualiza imediatamente apos nova avaliacao (sem cache — US-025.2).

---

## 3. REST interno — ticket-service (NOVO canal; ADR-T08 / ADR-T16)

### 3.1 GET /internal/tickets/participou   [US-024]  — callee da elegibilidade
event-service -> ticket-service. **Nao roteado pelo gateway** (`/api/internal/**` -> 404 externo).

**Auth:** `X-Internal-Token == ${INTERNAL_SHARED_SECRET}` (403 senao). Comparacao constante-no-tempo (`MessageDigest.isEqual`), espelha `InternalEventController`.

**Request:** `GET /internal/tickets/participou?usuarioId={u}&eventoId={e}` + header `X-Internal-Token`.

**Response 200:**
```java
record ParticipacaoResponse(boolean participou) {}
```
```json
{ "participou": true }
```
> `participou = true` sse existe (para `usuarioId`+`eventoId`): `Ingresso UTILIZADO` (de inscricao do usuario) **OU** `Inscricao ATIVA`. **A condicao "evento REALIZADO" e do event-service** (pre-filtro local antes de chamar este endpoint). O ticket nao conhece status de evento.

**Erros:**
| HTTP | code (message) | Quando |
|---|---|---|
| 403 | `ACESSO_INTERNO_NEGADO` | token ausente/errado |
| 400 | (param) | `usuarioId`/`eventoId` ausentes ou nao-numericos (nunca 500) |

---

## 4. AMQP — payload e routing

Exchange: **`ticketeira.events`** (topic, durable). DLX: **`ticketeira.dlx`**. Converter: `Jackson2JsonMessageConverter` + `JavaTimeModule` (OffsetDateTime ISO-8601 com offset).
`eventId` (UUID) **gerado na origem** (ticket-service `InscricaoCanceladaPublisher`) = chave de idempotencia (ADR-T11). Publicado em `afterCommit`.

### 4.1 inscricao.cancelada   [US-035 pago / US-042 individual]  — FILA NOVA
- **Routing key:** `inscricao.cancelada` · **Fila:** `inscricao.cancelada` (NOVA — declarar em definitions.json + payment RabbitConfig) · **DLQ:** `inscricao.cancelada.dlq`
- **Produtor:** ticket-service (`DELETE /tickets/inscricoes/{id}`, somente PAGO + dentro do prazo) · **Consumidor:** payment-service (unico -> 1 fila, sem fan-out)
- **Payload (record `InscricaoCanceladaEvent`):**
```java
public record InscricaoCanceladaEvent(
        UUID eventId,          // chave de idempotencia (origem = ticket)
        Long inscricaoId,      // mapeia 1:1 ao pagamento (pagamentos.inscricao_id UNIQUE)
        Long usuarioId,
        Long eventoId,
        OffsetDateTime ocorridoEm
) {}
```
```json
{ "eventId":"feedface-...-uuid", "inscricaoId":17, "usuarioId":24, "eventoId":10, "ocorridoEm":"2026-08-20T10:00:00-03:00" }
```
- **Efeito (payment, idempotente):**
  1. `INSERT processed_events(eventId, 'inscricao.cancelada')` — PK colide -> ACK no-op.
  2. `Pagamento p = findByInscricaoIdForUpdate(inscricaoId)` — ausente -> ACK no-op (defesa; gratuito nunca publica).
  3. `p.reembolsar()` (CONFIRMADO->REEMBOLSADO); se transicionou -> `Reembolso.criar(p.id, p.usuarioId, p.valorBruto, 'CANCELAMENTO_PARTICIPANTE')` + save.
  4. p nao-CONFIRMADO -> no-op, ACK (nunca poison message — CR-S4-01).

### 4.2 Resumo de filas (definitions.json apos 5B)
| Fila | Binding rk | Consumidor | DLQ |
|---|---|---|---|
| pedido.criado (existe) | pedido.criado | payment | pedido.criado.dlq |
| pagamento.aprovado (existe) | pagamento.aprovado | ticket | pagamento.aprovado.dlq |
| evento.finalizado (existe, 5A) | evento.finalizado | payment | evento.finalizado.dlq |
| evento.cancelado (existe, 5A) | evento.cancelado | payment | evento.cancelado.dlq |
| evento.cancelado.ticket (existe, 5A) | evento.cancelado | ticket | evento.cancelado.ticket.dlq |
| **inscricao.cancelada** (NOVA, 5B) | inscricao.cancelada | payment | **inscricao.cancelada.dlq** |

---

## 5. Constantes de routing
**ticket-service** (`InscricaoCanceladaPublisher` / RabbitConfig):
```java
EXCHANGE = "ticketeira.events";
RK_INSCRICAO_CANCELADA = "inscricao.cancelada";
```
**payment-service** (`RabbitConfig`):
```java
QUEUE_INSCRICAO_CANCELADA     = "inscricao.cancelada";
QUEUE_INSCRICAO_CANCELADA_DLQ = "inscricao.cancelada.dlq";
```
> ticket-service **so produz** `inscricao.cancelada` (declara a exchange — ja declara; opcional declarar a fila para os testes Testcontainers do ticket purgarem). payment declara fila+binding+DLQ.

---

## 6. Mudancas em DTOs internos (cross-service)

### 6.1 EventoInternoResponse (event) += dataInicio, prazoReembolsoDias   [US-035]
```java
public record EventoInternoResponse(
    Long id, String titulo, String tipo, String status,
    Integer vagasDisponiveis, Integer capacidade,
    BigDecimal preco, Long promotorId,
    OffsetDateTime dataInicio,      // NOVO (5B) — para o ticket checar o prazo
    Integer prazoReembolsoDias      // NOVO (5B) — janela de cancelamento (null se GRATUITO)
) {}
```
### 6.2 EventResumo (ticket, espelho) += dataInicio, prazoReembolsoDias
```java
public record EventResumo(
    Long id, String titulo, String tipo, String status,
    Integer vagasDisponiveis, Integer capacidade,
    BigDecimal preco, Long promotorId,
    OffsetDateTime dataInicio,      // NOVO
    Integer prazoReembolsoDias      // NOVO
) {}
```
> **Quebra de construtor:** os testes existentes que instanciam `EventResumo(...)` com 8 args (ex.: `InternalEventAuthTest`, `EventoCanceladoListenerIntegrationTest` nao usa) precisam dos 2 novos args. O Tester/Back atualizam as fixtures (passar `dataInicio`/`prazoReembolsoDias`, ex.: futuro + 7). Aditivo no JSON (campos a mais sao ignorados por consumidores antigos), mas o record Java muda de aridade.

## 7. Notas de borda (coding-standards)
- `{id}` nao-numerico em `DELETE /tickets/inscricoes/{id}` / `POST /events/{id}/avaliacoes` -> 400 (`MethodArgumentTypeMismatch`, handler base).
- Rota inexistente -> 404 (`NoResourceFoundException`, handler base).
- `participou` com query param invalido -> 400, nunca 500.
- Dinheiro nos reembolsos: `valor.setScale(2, HALF_UP)` (ja no `Reembolso.criar`); resposta com `@JsonFormat(shape=STRING)` ja aplicada no `PagamentoResponse` (5A).
