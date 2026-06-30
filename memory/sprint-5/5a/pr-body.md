## Sprint 5 · Trilha 5A — Repasse e reembolso (fim da saga financeira)

Fecha o ciclo financeiro do escrow (RF06): o dinheiro retido no Sprint 4 agora encontra destino — **repasse** ao promotor após o evento, ou **reembolso** se o evento é cancelado. Liga as últimas filas AMQP e dá ao **event-service** sua primeira mensageria.

### Histórias (aceite do PO: ACEITO COM RESSALVAS → confirmar neste CI)
- **US-043** — repasse (−10% taxa) aos pagamentos `CONFIRMADO` quando o evento vira `REALIZADO`.
- **US-042** (parte 5A) — reembolso em massa quando o promotor cancela o evento (`CANCELAMENTO_PARTICIPANTE` individual fica para a 5B).

### O que muda
- **event-service** (ganha RabbitMQ pela 1ª vez): `Evento.realizar()` + endpoint `POST /events/{id}/encerrar` (PUBLICADO→REALIZADO); `RabbitConfig` (delega ao autoconfigure) + `EventoPublisher` (publica `evento.finalizado`/`evento.cancelado` em `afterCommit`); `cancelar()` reseta `vagas_disponiveis = capacidade`; migration **V3** (`realizado_em`/`cancelado_em`).
- **payment-service**: consome `evento.finalizado` → **repasse** (UPDATE condicional em massa `WHERE evento_id=? AND status='CONFIRMADO'`); consome `evento.cancelado` → **reembolso em massa** (`REEMBOLSADO` + `reembolsos(EVENTO_CANCELADO)` integral). Migration **V3** (`pagamentos += evento_id, promotor_id, repassado_em, reembolsado_em` + índice + UNIQUE parcial). **TECH-S4-01 fechado** (`criarPendente` persiste `evento_id`/`promotor_id`, já presentes no `pedido.criado`).
- **ticket-service**: consome `evento.cancelado.ticket` (fan-out) → cancela inscrições/ingressos do evento em massa (preserva `UTILIZADO`). Sem migration.
- **frontend**: botão "Encerrar evento" (promotor) + status `REPASSADO`/`REEMBOLSADO` no extrato.
- **infra**: `definitions.json` += filas/bindings `evento.cancelado` (payment) e `evento.cancelado.ticket` (ticket) + DLQs.

### Concorrência / idempotência (ADR-T12, ADR-T13)
- **Fan-out** `evento.cancelado`: 1 fila por serviço (payment + `.ticket`), mesma routing key → ambos recebem.
- Idempotência: `processed_events(event_id)` na mesma tx do efeito; publish em `afterCommit`.
- **Corrida repasse-vs-reembolso**: transições condicionais `WHERE status='CONFIRMADO'` + row lock → exatamente um vence.

### Code review (Revisor, opus) — 0 P0/P1
Refactor `cancelar(eventoId, promotorId)` conferido em todos os call sites (correto); fiação AMQP do event-service validada (afterCommit, sem competing-consumers). 7 P2/P3 (consistência/dívida menor) → owner/backlog. Detalhe em `memory/sprint-5/5a/code-review.md`.

### Testes
- **Local:** reactor `mvn verify` SUCCESS · frontend `vite build` + `npm run test:run` 80/80 · regressão S2/S3/S4 verde.
- **Integração (Testcontainers PG + RabbitMQ):** repasse/reembolso idempotentes, corrida, fan-out — **executados neste CI** (não rodam no Windows local). Conferir verdes antes do merge.

### DoD
- [x] Migrations V3 (payment/event) aplicáveis.
- [x] Reactor compila + unit/regressão verdes (local).
- [ ] Suíte Testcontainers (saga financeira) verde **neste CI**.
- [x] Code review sem P0/P1.
- [x] Fluxos S2/S3/S4 intactos.

Não fazer merge automático — decisão humana.
