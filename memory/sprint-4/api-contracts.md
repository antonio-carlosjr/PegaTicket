# Sprint 4 — Contratos de API

> Endpoints REST + payloads AMQP da saga de inscricao paga. Erros **tipados** (codigo semantico + HTTP).
> Auth: gateway valida JWT e injeta `X-User-Id` / `X-User-Email` / `X-User-Verified` / `X-User-Papel`. Servicos NAO revalidam JWT.
> Todos os DTOs sao `record` com Bean Validation; `@Entity` nunca exposta (mapear via `Response.from(...)`).

---

## 1. POST /tickets/inscricoes  (via gateway: `POST /api/inscricoes`)  — ALTERADO

Aceita agora evento **PAGO**. Para GRATUITO o comportamento da S3 e **identico** (emite ingresso na hora, `status=ATIVA`).

**Auth:** header `X-User-Id` (401 `UNAUTHORIZED` se ausente).

### Request
```java
record InscricaoRequest(@NotNull(message = "eventoId e obrigatorio") Long eventoId) {}
```

### Response 201 (evento PAGO)
```java
// ingresso == null enquanto PENDENTE_PAGAMENTO; pagamento aponta o proximo passo (checkout)
record InscricaoResponse(
    Long id,
    Long eventoId,
    String status,            // "PENDENTE_PAGAMENTO" (PAGO) | "ATIVA" (GRATUITO)
    OffsetDateTime inscritoEm,
    IngressoResponse ingresso,        // null quando PENDENTE_PAGAMENTO
    PagamentoPendenteResponse pagamento  // null quando GRATUITO
) {}

record PagamentoPendenteResponse(
    Long inscricaoId,
    java.math.BigDecimal valor,   // = preco do evento
    String status                 // "AGUARDANDO" (pagamento ainda nao criado/confirmado)
) {}
```
> Nota: o `pagamento` aqui e uma **referencia de checkout** montada pelo ticket-service a partir do `preco` (vindo do `EventResumo`); o registro real de `Pagamento` nasce no payment-service ao consumir `pedido.criado` (assincrono).

### Erros (`ErrorResponse` tipado: timestamp, status, error, message, path)
| HTTP | code (`message`) | Quando |
|---|---|---|
| 401 | `UNAUTHORIZED` | `X-User-Id` ausente |
| 404 | `EVENTO_NAO_ENCONTRADO` | evento inexistente |
| 409 | `JA_INSCRITO` | UNIQUE(usuario,evento) — pre-check ou corrida |
| 409 | `EVENTO_ESGOTADO` | reserva atomica falhou (vagas=0) |
| 422 | `EVENTO_NAO_PUBLICADO` | status != PUBLICADO |
| 503 | `EVENTO_INDISPONIVEL` | event-service fora / 403 token interno |

### Eventos emitidos
- **`pedido.criado`** (so se evento PAGO), publicado em `afterCommit`. Payload em §6.

---

## 2. POST /payments/{inscricaoId}/confirmar  (via gateway: `POST /api/payments/{inscricaoId}/confirmar`)  — NOVO

Gateway **SIMULADO** aprova o pagamento e retem em escrow. **Idempotente**: 2a chamada com pagamento ja `CONFIRMADO` -> no-op, **nao** republica `pagamento.aprovado`.

**Auth:** `X-User-Id` (401 se ausente). Confere ownership (`pagamento.usuarioId == X-User-Id`).

### Request
- Sem corpo (1 toque — criterio US-040.2). `inscricaoId` no path.

### Response 200
```java
record PagamentoResponse(
    Long id,
    Long inscricaoId,
    Long usuarioId,
    java.math.BigDecimal valorBruto,
    java.math.BigDecimal valorTaxa,      // round(bruto * 0.10, 2)
    java.math.BigDecimal valorRepasse,   // bruto - taxa (computado, NAO liberado)
    String status,                       // "CONFIRMADO"
    String gateway,                      // "SIMULADO"
    String gatewayPaymentId,             // ex.: "SIM-<uuid>"
    OffsetDateTime processadoEm,
    OffsetDateTime criadoEm
) {}
```

### Erros
| HTTP | code | Quando |
|---|---|---|
| 401 | `UNAUTHORIZED` | `X-User-Id` ausente |
| 403 | `PAGAMENTO_DE_OUTRO_USUARIO` | `pagamento.usuarioId != X-User-Id` |
| 404 | `PAGAMENTO_NAO_ENCONTRADO` | sem `Pagamento` para o `inscricaoId` (saga `pedido.criado` ainda nao chegou, ou inscricao GRATUITA) |
| 409 | `INSCRICAO_EXPIRADA` | inscricao foi expirada pelo job de TTL (vaga ja liberada) |
| 402 | `PAGAMENTO_RECUSADO` | gateway SIMULADO devolveu falha (mensagem amigavel no front) |

### Eventos emitidos
- **`pagamento.aprovado`** (so na transicao PENDENTE->CONFIRMADO), publicado em `afterCommit`. Payload em §6.

---

## 3. GET /payments/inscricao/{inscricaoId}  (via gateway: `GET /api/payments/inscricao/{inscricaoId}`)  — NOVO

Status do pagamento de uma inscricao do **proprio** usuario (frontend faz polling do checkout aqui ou em "Meus ingressos").

**Auth:** `X-User-Id`. Ownership obrigatorio.

### Response 200
- `PagamentoResponse` (vide §2). `status` pode ser `PENDENTE` (escrow ainda nao confirmado) ou `CONFIRMADO`.

### Erros
| HTTP | code | Quando |
|---|---|---|
| 401 | `UNAUTHORIZED` | header ausente |
| 403 | `PAGAMENTO_DE_OUTRO_USUARIO` | nao e dono |
| 404 | `PAGAMENTO_NAO_ENCONTRADO` | sem pagamento (ainda nao criado / GRATUITO) |

---

## 4. GET /payments/me  (via gateway: `GET /api/payments/me`)  — NOVO

Lista pagamentos do usuario autenticado (mais recente primeiro).

**Auth:** `X-User-Id`. **Response 200:** `List<PagamentoResponse>` (so do `usuarioId`). Vazio -> `[]`.

---

## 5. GET /payments  (via gateway: `GET /api/payments`)  — NOVO (Admin / auditoria de escrow)

Listagem paginada de pagamentos para o Admin auditar o escrow (US-040.3): ve cada pagamento `CONFIRMADO` com `valor_bruto`/`valor_taxa`/`valor_repasse` e o `inscricaoId`/`usuarioId`.

**Auth:** `X-User-Id` + **papel ADMIN** (`X-User-Papel == ADMIN`, ja disponivel desde US-051). Sem papel ADMIN -> 403 `ACESSO_NEGADO`.
> Ponto a validar com o PO (Fase 3): confirmar que o gateway injeta `X-User-Papel`. Se nao, marcar divida ADR-T01 e liberar para autenticado em homolog.

### Query params
- `page` (default 0), `size` (default 20, cap 100), `status` (opcional: filtra `PENDENTE`/`CONFIRMADO`).

### Response 200
```java
Page<PagamentoResponse>
```

### Erros
| HTTP | code | Quando |
|---|---|---|
| 401 | `UNAUTHORIZED` | header ausente |
| 403 | `ACESSO_NEGADO` | papel != ADMIN |

---

## 6. GET /internal/events/{id}  (event-service, canal interno — ADR-T08)  — ALTERADO

Adiciona `preco` e `promotorId`. **Nao** roteado pelo gateway; autorizado por `X-Internal-Token`.

### Response 200 (`EventoInternoResponse` — DELTA: +preco +promotorId)
```java
record EventoInternoResponse(
    Long id,
    String titulo,
    String tipo,                  // "GRATUITO" | "PAGO"
    String status,                // "RASCUNHO" | "PUBLICADO" | "REALIZADO" | "CANCELADO"
    Integer vagasDisponiveis,
    Integer capacidade,
    java.math.BigDecimal preco,   // NOVO — null se GRATUITO
    Long promotorId               // NOVO — para repasse (S5)
) {}
```
**Espelhar no cliente:** `EventResumo` (ticket-service) ganha `BigDecimal preco` e `Long promotorId`.

### Erros
| HTTP | code | Quando |
|---|---|---|
| 403 | `ACESSO_INTERNO_NEGADO` | `X-Internal-Token` ausente/errado |
| 404 | `EVENTO_NAO_ENCONTRADO` | evento inexistente — **e** o que o gateway devolve a tentativa externa (rota `/api/internal/**` nao existe) |

---

## 7. Payloads dos eventos AMQP

**Exchange:** `ticketeira.events` (topic, durable). **Converter:** `Jackson2JsonMessageConverter`. **Filas/bindings:** ja em `infra/rabbitmq/definitions.json` (sem alteracao). DLX: `ticketeira.dlx` -> `*.dlq`.

### 7.1 `pedido.criado`  (routing key `pedido.criado`) — ticket-service -> payment-service
```java
record PedidoCriadoEvent(
    UUID eventId,                 // gerado na ORIGEM; chave de idempotencia (processed_events)
    Long inscricaoId,
    Long usuarioId,
    Long eventoId,
    java.math.BigDecimal valor,   // = preco do evento (valor_bruto)
    Long promotorId,              // para escrow/repasse (S5)
    OffsetDateTime ocorridoEm
) {}
```
Publicado em `afterCommit` da tx que cria `Inscricao(PENDENTE_PAGAMENTO)`.

### 7.2 `pagamento.aprovado`  (routing key `pagamento.aprovado`) — payment-service -> ticket-service
```java
record PagamentoAprovadoEvent(
    UUID eventId,                 // gerado na ORIGEM; chave de idempotencia
    Long pagamentoId,
    Long inscricaoId,
    Long usuarioId,
    Long eventoId,
    OffsetDateTime ocorridoEm
) {}
```
Publicado em `afterCommit` da tx que confirma o `Pagamento` (so na transicao PENDENTE->CONFIRMADO).

### Contrato de idempotencia do consumidor (ambos)
1. Recebe a mensagem (at-least-once -> pode repetir).
2. Na **mesma tx** do efeito: `INSERT INTO processed_events(event_id, routing_key)` com o `eventId` do payload.
3. PK ja existe -> tx desfaz, consumidor **ACK** (no-op). Senao, aplica o efeito (cria Pagamento / emite Ingresso) e commita.
4. Excecao nao tratada -> sem ACK -> RabbitMQ re-entrega; apos limite -> DLQ.
