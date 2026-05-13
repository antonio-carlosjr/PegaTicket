# ADR 0004 — RabbitMQ com topic exchange + DLQ para eventos de domínio

**Status:** Aceito · 2026-05-12

## Contexto

RNF05 exige que pagamento, reembolso e repasse sejam **assíncronos**. RF03/RNF02 exigem inscrição confiável sob alta concorrência. O fluxo principal:

```
Ticket Service → publica PedidoCriado → Payment Service processa pagamento
Payment Service → publica PagamentoAprovado → Ticket Service confirma ingresso
Event Service → publica EventoFinalizado → Payment Service libera repasse
```

## Decisão

Mensageria via **RabbitMQ 3.13** com:

- **Topic exchange** `ticketeira.events` (durable).
- **3 filas de domínio** (`pedido.criado`, `pagamento.aprovado`, `evento.finalizado`).
- **Topic exchange Dead Letter** `ticketeira.dlx` + **3 DLQs** correspondentes (`*.dlq`).
- Cada fila configurada com `x-dead-letter-exchange: ticketeira.dlx`.
- Topologia declarada em `infra/rabbitmq/definitions.json` (carregada no boot do broker).

Mensagens são JSON. **Idempotência** dos consumidores é responsabilidade do serviço (ex.: `payment-service` valida se já existe `Pagamento` com a `inscricao_id` antes de criar). A obrigatoriedade fica registrada no `Sprint 1` para implementação.

## Consequências

✅ Acoplamento temporal removido — `ticket-service` não trava esperando pagamento.
✅ Resiliência: se `payment-service` cair, mensagens ficam na fila e são consumidas na volta.
✅ DLQ preserva mensagens que falham repetidamente — debug + reprocessamento manual via Management UI.
✅ Topology como código: novo dev clona o repo e tem a topologia idêntica.

❌ **Consistência eventual** — usuário pode ver "inscrição pendente" por alguns segundos entre `PedidoCriado` e `PagamentoAprovado`. Aceitável: UX deve indicar "processando".
❌ Idempotência obrigatória nos consumers — sem isso, retries criam duplicatas.
❌ Ordering NÃO é garantido entre exchanges diferentes (apenas dentro da mesma fila). Não problemático no design atual.

Em Sprint 1, cada consumer terá `@RabbitListener` + tabela de idempotência (`processed_events(event_id, processed_at)`) para descartar duplicatas.
