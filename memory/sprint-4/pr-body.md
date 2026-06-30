## Sprint 4 — Pagamento + escrow + saga de inscrição paga

Implementa o fluxo financeiro central (RF05): o participante se inscreve num evento **PAGO**, paga por um **gateway simulado** com o dinheiro **retido em escrow**, e o **ingresso só é emitido após `pagamento.aprovado`** — via uma **saga assíncrona** orientada a eventos (RabbitMQ), **idempotente** e à prova de concorrência. Liga, pela primeira vez, a mensageria (que existia só como topologia declarada).

### Histórias (aceite do PO: ACEITO COM RESSALVAS)
- **US-040** — pagar evento PAGO (gateway simulado) com retenção em escrow.
- **US-041** — emitir ingresso somente após `pagamento.aprovado` (estado intermediário `PENDENTE_PAGAMENTO`).
- **US-060** — consumidores RabbitMQ idempotentes (`processed_events`) ligados (fecha a dívida ADR-T04).

### O que muda
- **payment-service** (de stub → real): `Pagamento`/escrow (taxa 10% HALF_UP, repasse computado e **retido**), `GatewaySimulado`, consumidor `pedido.criado` + produtor `pagamento.aprovado` (idempotentes, publish em `afterCommit`), endpoints `confirmar`/`me`/`inscricao/{id}`/`payments` (admin), Swagger.
- **ticket-service**: ramo PAGO da saga em `InscricaoService` (reserva vaga ADR-T07 → `PENDENTE_PAGAMENTO` sem ingresso → publica `pedido.criado`); consumidor `pagamento.aprovado` (emite ingresso + `ATIVA`); `ExpiracaoReservaJob` (TTL 30 min libera vaga de pagamento abandonado). Fluxo **GRATUITO da S3 intacto**.
- **event-service**: `EventoInternoResponse` expõe `preco`/`promotorId`.
- **frontend**: `CheckoutPage` (pagar em 1 toque, aviso "Pague em até 30 min", polling com timeout de 60s), estado pendente em "Meus ingressos".
- **Migrations**: `ticket V2` (`inscricoes.status` += `PENDENTE_PAGAMENTO`/`EXPIRADA`; `processed_events`; índice parcial) · `payment V2` (`processed_events`).

### Concorrência / idempotência (ADR-T10, ADR-T11)
- Última vaga PAGO: `UPDATE ... vagas-1 WHERE vagas>0` (ADR-T07) **antes** do pagamento → 1 reserva, K−1×409.
- Exactly-once-effect: `event_id` (UUID na origem) + `processed_events` na **mesma tx** do efeito + publish em `afterCommit`; `UNIQUE(inscricao_id)` em ingressos/pagamentos como rede final.
- Escrow: `CONFIRMADO` retido; reembolso/repasse ficam para a **Sprint 5**.

### Code review (Revisor, opus) — 0 P0/P1 em aberto
- **CR-S4-01 (P0)** corrigido: guard de estado evita poison-message quando `pagamento.aprovado` chega para inscrição `EXPIRADA`.
- **CR-S4-02/03/04 (P1)** corrigidos: TTL fora de tx; handlers de borda (sem 500 em query inválida); `/tickets/me` com LEFT JOIN inclui pendente (UI antes morta).
- P2/P3 (CR-S4-05..11) → backlog/owner. Detalhe em `memory/sprint-4/code-review.md`.

### Testes
- **Local:** reactor `mvn verify` BUILD SUCCESS (0 falhas) · frontend `vite build` + `npm run test:run` 74/74. Regressão GRATUITO verde.
- **Integração (Testcontainers Postgres + RabbitMQ):** concorrência da última vaga, idempotência de reentrega, escrow e auth — **executadas neste CI** (não rodam no Windows local). Conferir verdes antes do merge.

### Riscos / follow-ups (não bloqueiam)
- **TECH-S4-01**: persistir `evento_id`/`promotor_id` em `pagamentos` (necessário p/ repasse na S5).
- **TECH-S4-02**: tela admin de escrow (endpoint existe; sem UI) → S5 alta prioridade.
- **TECH-S4-03**: prazo restante no card pendente.

### DoD
- [x] Migrations Flyway aplicáveis (`ddl-auto: validate`).
- [x] Reactor compila + unit/regressão verdes (local).
- [ ] Suíte Testcontainers (concorrência/idempotência) verde **neste CI**.
- [x] Swagger do payment-service.
- [x] Fluxo GRATUITO da S3 intacto.
- [x] Code review sem P0/P1.

Não fazer merge automático — decisão humana.
