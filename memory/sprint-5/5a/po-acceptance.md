# Sprint 5 · Trilha 5A — Aceite do PO

> Papel: Product Owner (persona Marina / Bruno / Admin).
> Data: 2026-06-30.
> Base: `po-planning.md` (critérios US-042/US-043), `po-validation.md` (APROVADO COM RESSALVAS RA1/RA2/RA3),
> `test-report.md` (build LOCAL VERDE; integração Testcontainers PENDENTE de CI), `bugs.md` (0 P0/P1 local).

---

## Veredicto por história

| História | Veredicto | Cenário verificado | Observação |
|---|---|---|---|
| **US-043** — Repasse (−10%) após evento REALIZADO | ✅ **ACEITO** condicional ao CI | Marina clica "Encerrar" → evento `REALIZADO` → extrato mostra `REPASSADO` com `valor_bruto × 0,90`. Endpoint + listener + frontend entregues; idempotência de reentrega verifica no CI. | Invariante B2 (repasse 1× por pagamento) e corrida C2 só confirmados no CI (Testcontainers). Sem CI verde, veredicto passa a ⚠️ e entra no loop de `/validar-sprint 5a`. |
| **US-042** — Reembolso por evento cancelado (em massa) — parte 5A | ✅ **ACEITO** condicional ao CI | Marina cancela evento → todos `CONFIRMADO` viram `REEMBOLSADO` + registro `reembolsos(EVENTO_CANCELADO)`; inscrições/ingressos `CANCELADO`; vagas resetadas à capacidade. Bruno vê `REEMBOLSADO` no extrato. Endpoint + listener + ticket-consumer entregues. Idempotência verifica no CI. | Invariante C1 (reembolso em massa 1× por pagamento), fan-out `evento.cancelado.ticket` (D1/D2) e corrida C2 só confirmados no CI. Reembolso individual (`CANCELAMENTO_PARTICIPANTE`, US-035) corretamente fora do escopo — 5B. |

---

## Status das ressalvas da validação (RA1 / RA2 / RA3)

| Ressalva | Exigência do PO | Status | Evidência |
|---|---|---|---|
| **RA1** — UNIQUE parcial em `reembolsos` | Ativar `uk_reembolso_evento_cancelado ON reembolsos(pagamento_id) WHERE motivo='EVENTO_CANCELADO'` na migration V3 (descomentar DDL). Defesa em profundidade contra reprocessamento manual. | ✅ **Tratada** | `data-model.md` §A confirma índice ativo na migration `V3__repasse_reembolso.sql`; `backend-log.md` registra a inclusão. |
| **RA2** — `eventoId` em pagamentos no admin | `GET /api/payments` (admin) deve exibir `eventoId` para distinguir pagamentos pós-V3 de legados (`evento_id = NULL`). Auditabilidade. | ✅ **Tratada** | `api-contracts.md` §2 e `PagamentoResponse` incluem `eventoId`; campo nullable, pagamentos legados aparecem como `null` — Admin identifica sem ambiguidade. |
| **RA3** — Botão "Encerrar evento" no frontend | Botão entregue na 5A (não bloqueante para aceite da API, mas exigido para narrativa da demo presencial). | ✅ **Tratada** | `test-report.md` confirma `frontend 80/80` incluindo testes do botão "Encerrar"; `EncerrarEventoControllerTest` 6/6. Marina consegue encerrar o evento sem curl. |

**Todas as 3 ressalvas foram endereçadas.** Não há ressalvas abertas da validação.

---

## Critérios de aceite — verificação por ator

### Marina (promotora, 35) — US-043 (repasse)

| Critério | Status |
|---|---|
| 1. Evento `REALIZADO` → todos `CONFIRMADO` transitam para `REPASSADO` com `valor_repasse = valor_bruto × 0,90` | ✅ unit local (`PagamentoRepassarReembolsarTest` 6/6); invariante transacional ✅ CI pendente |
| 2. Marina vê `REPASSADO` com valor correto em `/api/payments/me` sem relogar | ✅ local (endpoint + extrato frontend 80/80) |
| 3. Reentrega de `evento.finalizado` → repasse aplicado exatamente 1× (`processed_events`) | ✅ unitário; ✅ CI pendente (B2.b) |
| 4. Pagamento já em status diferente de `CONFIRMADO` não é tocado | ✅ unit local (B2.c); ✅ CI pendente |

### Bruno (participante, 24) — US-042 (reembolso em massa)

| Critério | Status |
|---|---|
| 1. Marina cancela evento → `CONFIRMADO→REEMBOLSADO` + `reembolsos(EVENTO_CANCELADO)`; inscrições/ingressos `CANCELADO`; vagas = capacidade | ✅ unit local (C1 payment, D1 ticket); CI pendente (invariante transacional) |
| 4. Reentrega de `evento.cancelado` → reembolso aplicado exatamente 1× | ✅ unitário; ✅ CI pendente (C1.b) |
| 5. Admin lista `REEMBOLSADO` e `REPASSADO` para auditoria | ✅ endpoint `GET /api/payments?status=...` + `eventoId` no response |

### Admin (operação)

| Critério | Status |
|---|---|
| Pagamentos com `eventoId` visível para distinguir legados | ✅ RA2 tratada — `PagamentoResponse.eventoId` incluso |
| Auditoria de `REPASSADO` e `REEMBOLSADO` via `GET /api/payments` | ✅ contrato e testes confirmam (C1.d) |

---

## Cenários de concorrência — o que está e o que aguarda CI

| Cenário | Cobertura local | CI (Testcontainers) |
|---|---|---|
| `evento.finalizado` reentregue 2× → apenas 1 `REPASSADO` por pagamento | Unit (`processed_events` lógica) ✅ | **B2.b** — pendente |
| `evento.cancelado` reentregue 2× → apenas 1 `REEMBOLSADO` por pagamento | Unit ✅ | **C1.b** — pendente |
| Corrida repasse-vs-reembolso → exatamente 1 vence (nenhum estado inválido) | Unit (`UPDATE WHERE status='CONFIRMADO'`) ✅ | **C2** `@RepeatedTest(3)` — pendente |
| Fan-out: payment + ticket consomem `evento.cancelado` (routing keys distintas) | Unit isolado por serviço ✅ | **D1/D2** fan-out — pendente |

> **Lição do S4 aplicada:** event-service ganhou RabbitMQ pela 1ª vez nesta trilha; bugs de fiação AMQP se revelam no CI. O agente já removeu exclusões de `RabbitAutoConfiguration`, configurou `TestcontainersBase` singleton e purga de filas no `@BeforeEach`. Ponto de atenção permanece registrado — o CI pode exigir ajuste de configuração sem alterar comportamento de produto.

---

## Histórias devolvidas ao backlog

_Nenhuma história da Trilha 5A é devolvida._

- **US-042 critérios 2/3** (reembolso individual `CANCELAMENTO_PARTICIPANTE`, bloqueio fora-do-prazo) → **Trilha 5B** conforme planejado (intencional, não é devolução — escopo foi corretamente recortado desde o po-planning).

---

## Aprovação

```
[x] ACEITO COM RESSALVAS  →  confirmar CI verde via /validar-sprint 5a
[ ] ACEITO (sem condições)
[ ] REPROVADO
```

**Justificativa:**

A Trilha 5A entregou todos os artefatos necessários: endpoints REST (`/events/{id}/encerrar`, `/events/{id}/cancelar`), listeners AMQP (`RepaymentListener`, `RefundListener`, `TicketCancelListener`), migration V3 com UNIQUE parcial (RA1 ✅), `PagamentoResponse.eventoId` (RA2 ✅) e botão "Encerrar" no frontend (RA3 ✅). Build local e regressão inteiros (0 falhas). As 3 ressalvas da `po-validation.md` foram endereçadas.

O aceite é **condicional ao CI** porque os invariantes mais críticos de produto — idempotência de reentrega em massa, corrida repasse-vs-reembolso, fan-out multi-serviço — só são exercitados pelo Testcontainers (Postgres + RabbitMQ reais). Essa é exatamente a condição do Sprint 4 que precedeu este, e o padrão do projeto.

**Se o CI for verde: aceite passa a ACEITO pleno.**
**Se o CI revelar falha de fiação AMQP ou invariante: entra no loop de correção do `/validar-sprint 5a` antes de o aceite ser emitido.**

> Como Marina, eu quero ver o repasse no extrato após clicar "Encerrar" — e isso está entregue.
> Como Bruno, eu quero ver `REEMBOLSADO` após Marina cancelar — e isso está entregue.
> O que falta é a confirmação de que isso resiste à pressão de concorrência real. Essa confirmação vem do CI.
