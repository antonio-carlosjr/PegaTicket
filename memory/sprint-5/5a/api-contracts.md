# Sprint 5 — Trilha 5A (Financeiro) — Contratos de API

> REST (via gateway) + payloads AMQP. Erros tipados (codigo semantico) traduzidos pelo `GlobalExceptionHandler` em `ErrorResponse{timestamp,status,error,message,path}`.
> Convencao: rotas internas do servico sem `/api`; via gateway com `/api` + StripPrefix.

---

## 1. REST — event-service

### 1.1 POST /events/{id}/encerrar  (via gateway: POST /api/events/{id}/encerrar)   [US-043]
Marca o evento PUBLICADO como REALIZADO e dispara o repasse (publica `evento.finalizado`).

**Auth:** `X-User-Id` (gateway) + `X-User-Papel` (gateway) == `PROMOTOR` + **ownership** (`evento.promotorId == userId`).

**Request:** sem body.

**Response 200** (`EventoResponse`, ja existente — agora com `status="REALIZADO"`):
```java
// EventoResponse.from(evento)  — inalterado; status passa a poder ser REALIZADO
{ "id":10, "titulo":"...", "status":"REALIZADO", "promotorId":7, ... }
```

**Erros (ErrorResponse tipado):**
| HTTP | code | Quando |
|---|---|---|
| 401 | `Autenticacao obrigatoria.` | `X-User-Id` ausente |
| 403 | `Acesso restrito a promotores.` | `X-User-Papel != PROMOTOR` |
| 404 | `Evento nao encontrado.` | evento inexistente **ou** nao e do promotor (nao vaza existencia — padrao `carregarComOwnership`) |
| 409 | `TRANSICAO_INVALIDA` | evento nao esta PUBLICADO (RASCUNHO/CANCELADO/ja REALIZADO) |

**Evento emitido:** `evento.finalizado` (apos commit). Ver §3.1.

### 1.2 POST /events/{id}/cancelar  (JA EXISTE — sem mudanca de contrato)   [US-042]
Inalterado no contrato REST (PROMOTOR + owner; RASCUNHO|PUBLICADO→CANCELADO; 409 `EVENTO_JA_CANCELADO`/`TRANSICAO_INVALIDA`).
**Mudanca de comportamento:** agora **publica `evento.cancelado` em afterCommit** (ver §3.2). Resposta REST identica.

> **Nota de implementacao:** se o PO aprovar resetar vagas (architecture §Vagas), `Evento.cancelar()` passa a setar `vagasDisponiveis = capacidade` — nao altera o contrato REST (campo ja existe na resposta).

---

## 2. REST — payment-service (consultas — sem endpoint novo obrigatorio)

`GET /api/payments/me` e `GET /api/payments` (admin) **ja existem** e ja serializam `status`. Apos a 5A passam a refletir `REPASSADO`/`REEMBOLSADO` naturalmente (o enum ja os tem; o `PagamentoResponse` ja expoe `status`). **Nenhum endpoint novo necessario** para os criterios do PO (Marina/Bruno veem o status no extrato; admin filtra por `?status=REPASSADO`/`REEMBOLSADO` no `GET /api/payments` ja existente).

**Opcional (so se o PO pedir auditoria de reembolsos):** `GET /api/payments/{id}/reembolsos` (admin) listando os `reembolsos` de um pagamento. **Nao incluido por padrao** (YAGNI; o status no extrato basta).

> `PagamentoResponse` ganha 2 campos opcionais para o extrato refletir o destino do dinheiro (nao-breaking, additive):
```java
record PagamentoResponse(
   Long id, Long inscricaoId, Long usuarioId,
   @JsonFormat(shape=STRING) BigDecimal valorBruto,
   @JsonFormat(shape=STRING) BigDecimal valorTaxa,
   @JsonFormat(shape=STRING) BigDecimal valorRepasse,
   String status, String gateway, String gatewayPaymentId,
   OffsetDateTime processadoEm, OffsetDateTime criadoEm,
   Long eventoId,        // NOVO (TECH-S4-01) — null em pagamentos legados
   Long promotorId       // NOVO
) {}
```

---

## 3. AMQP — payloads e routing

Exchange: **`ticketeira.events`** (topic, durable). DLX: **`ticketeira.dlx`**. Converter: `Jackson2JsonMessageConverter` + `JavaTimeModule` (OffsetDateTime ISO-8601 com offset, nao timestamp).
`eventId` (UUID) **gerado na origem** (event-service `EventoPublisher`) = chave de idempotencia (ADR-T11). Publicado em `afterCommit`.

### 3.1 evento.finalizado   [US-043]
- **Routing key:** `evento.finalizado` · **Fila:** `evento.finalizado` (JA declarada em definitions.json + payment RabbitConfig) · **DLQ:** `evento.finalizado.dlq`
- **Produtor:** event-service (`POST /events/{id}/encerrar`) · **Consumidor:** payment-service
- **Payload (record `EventoFinalizadoEvent`):**
```java
public record EventoFinalizadoEvent(
        UUID eventId,          // chave de idempotencia (origem)
        Long eventoId,         // id do evento realizado
        Long promotorId,       // para quem e o repasse
        OffsetDateTime ocorridoEm
) {}
```
```json
{ "eventId":"c0ffee00-...-uuid", "eventoId":10, "promotorId":7, "ocorridoEm":"2026-06-30T18:00:00-03:00" }
```
- **Efeito (payment):** `UPDATE pagamentos SET status='REPASSADO', repassado_em=now WHERE evento_id=:eventoId AND status='CONFIRMADO'`. `valor_repasse` ja computado no S4. Idempotente via `processed_events(eventId)`.

### 3.2 evento.cancelado   [US-042]  — FILA NOVA
- **Routing key:** `evento.cancelado` · **Fila:** `evento.cancelado` (NOVA — declarar em definitions.json + payment RabbitConfig + ticket RabbitConfig) · **DLQ:** `evento.cancelado.dlq`
- **Produtor:** event-service (`POST /events/{id}/cancelar`) · **Consumidores:** payment-service **e** ticket-service (fan-out: 1 binding por servico na mesma fila? **Nao** — uma fila por servico nao; ver nota abaixo)
- **Payload (record `EventoCanceladoEvent`):**
```java
public record EventoCanceladoEvent(
        UUID eventId,          // chave de idempotencia (origem)
        Long eventoId,         // id do evento cancelado
        Long promotorId,
        OffsetDateTime ocorridoEm
) {}
```
```json
{ "eventId":"dead10cc-...-uuid", "eventoId":10, "promotorId":7, "ocorridoEm":"2026-06-30T18:05:00-03:00" }
```
- **Efeito (payment):** para cada `pagamento CONFIRMADO` do `eventoId`: `→ REEMBOLSADO` + `INSERT reembolsos(motivo='EVENTO_CANCELADO', status='PROCESSADO', valor=valor_bruto)`.
- **Efeito (ticket):** `inscricoes ATIVA|PENDENTE_PAGAMENTO → CANCELADA` + `ingressos ATIVO → CANCELADO` do `eventoId`.

> **Topologia do fan-out (decisao):** payment e ticket sao consumidores **independentes** do mesmo evento. Com topic exchange, cada servico precisa da **sua propria fila** ligada a routing key `evento.cancelado` para receber **uma copia cada** (se compartilhassem 1 fila, so um consumiria — competing consumers). Portanto:
> - **payment:** fila `evento.cancelado` (declarada na RabbitConfig do payment + em definitions.json) com binding `ticketeira.events → evento.cancelado` (rk `evento.cancelado`).
> - **ticket:** fila **dedicada** `evento.cancelado.ticket` (declarada na RabbitConfig do ticket + em definitions.json) com binding `ticketeira.events → evento.cancelado.ticket` (rk `evento.cancelado`).
> Assim os dois recebem a copia. DLQ por fila: `evento.cancelado.dlq` e `evento.cancelado.ticket.dlq`.
> (Mesma logica vale para qualquer evento fan-out; `evento.finalizado` so tem 1 consumidor (payment), entao 1 fila basta.)

### 3.3 Resumo de filas (definitions.json apos 5A)
| Fila | Binding rk | Consumidor | DLQ |
|---|---|---|---|
| `pedido.criado` (existe) | `pedido.criado` | payment | `pedido.criado.dlq` |
| `pagamento.aprovado` (existe) | `pagamento.aprovado` | ticket | `pagamento.aprovado.dlq` |
| `evento.finalizado` (existe) | `evento.finalizado` | payment | `evento.finalizado.dlq` |
| **`evento.cancelado`** (NOVA) | `evento.cancelado` | payment | **`evento.cancelado.dlq`** |
| **`evento.cancelado.ticket`** (NOVA) | `evento.cancelado` | ticket | **`evento.cancelado.ticket.dlq`** |

---

## 4. Constantes de routing (event-service RabbitConfig — NOVA)
```java
public static final String EXCHANGE_EVENTS = "ticketeira.events";
public static final String EXCHANGE_DLX    = "ticketeira.dlx";
public static final String RK_EVENTO_FINALIZADO = "evento.finalizado";
public static final String RK_EVENTO_CANCELADO  = "evento.cancelado";
```
event-service **so produz** (nao declara filas de consumo obrigatorias; declara as exchanges para o publish nao falhar). Recomenda-se declarar tambem as filas/bindings para alinhar com definitions.json (RabbitAdmin idempotente).

## 5. Notas de borda (coding-standards)
- `POST /events/{id}/encerrar` com `{id}` nao-numerico → 400 (`MethodArgumentTypeMismatch`, ja tratado pelo handler base).
- Rota inexistente → 404 (`NoResourceFoundException`, ja tratado).
- Param de query invalido em consultas admin → 400, nunca 500.
