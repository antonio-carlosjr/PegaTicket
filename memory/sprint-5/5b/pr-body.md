## Sprint 5 · Trilha 5B — Experiência do participante (check-in, cancelamento+reembolso individual, avaliação/reputação)

Fecha o ciclo de experiência: **Marina valida o QR na porta**, **Bruno cancela a inscrição e recebe o reembolso individual**, e **participantes avaliam o evento** com reputação agregada. Complementa a saga financeira da 5A com o reembolso `CANCELAMENTO_PARTICIPANTE` e dá ao **event-service** sua primeira mensageria **outbound síncrona** (`TicketClient`).

### Histórias (aceite do PO: ACEITO COM RESSALVAS → confirmar neste CI)
- **US-034** — check-in por QR na porta (promotor dono; `ATIVO→UTILIZADO` + `checkins`).
- **US-035** — cancelar inscrição com política de prazo; se PAGO dentro do prazo → **reembolso individual** (`CANCELAMENTO_PARTICIPANTE`).
- **US-024** — avaliar evento (nota 1-5, elegibilidade via canal interno).
- **US-025** — reputação do evento (média/total) no detalhe. *(aceite pleno — sem condição de CI)*

### O que muda
- **ticket-service**: `CheckinController` (`POST /tickets/checkin`, PROMOTOR + ownership do evento via `EventClient` → 403 alheio) + `CheckinService` + `Checkin`/`Ingresso.realizarCheckin()` (409 se UTILIZADO/CANCELADO); `CancelamentoController` (`DELETE /tickets/inscricoes/{id}`) + `CancelamentoInscricaoService` (transição condicional `cancelarPorParticipante` 0→409; política de prazo 422; publica `inscricao.cancelada` em `afterCommit` se PAGO no prazo); `InternalTicketController` (`GET /internal/tickets/participou`, token interno constante-no-tempo). `EventResumo += dataInicio/prazoReembolsoDias`.
- **event-service**: `Avaliacao` (nota 1-5, `UNIQUE(evento,usuario)`) + `AvaliacaoController`/`AvaliacaoService` (valida elegibilidade via **`TicketClient`** outbound, fail-closed 503); `ReputacaoResponse` (AVG+COUNT em 1 query); `EventoResponse += reputacao`; `EventoInternoResponse += dataInicio/prazoReembolsoDias`.
- **payment-service**: `InscricaoCanceladaListener` consome `inscricao.cancelada` → `reembolsarPorInscricao(...)` (reusa mecanismo de reembolso da 5A, motivo `CANCELAMENTO_PARTICIPANTE`).
- **frontend**: telas `CheckinScanner` (promotor), `CancelarInscricao` (mostra `reembolsoIniciado`/prazo), `AvaliacaoEvento` (nota 1-5).
- **infra**: `definitions.json` += fila/binding `inscricao.cancelada` (payment).

### Concorrência / idempotência (ADR-T14, ADR-T15, ADR-T16)
- **Check-in duplo do mesmo QR**: `UNIQUE(ingresso_id)` em `checkins` (barreira atômica) → 1 vence, 2º → 409.
- **Duplo cancelamento**: `UPDATE inscricoes SET status=CANCELADA WHERE id=? AND status IN (ATIVA,PENDENTE_PAGAMENTO)` → rowsAffected; 1=ok, 0=409. Row lock serializa.
- **Reembolso individual idempotente + corrida individual-vs-massa**: `processed_events(event_id)` na mesma tx + transição condicional `reembolsar()` `WHERE status='CONFIRMADO'` sob lock pessimista → exatamente 1 reembolso por pagamento (motivos distintos `CANCELAMENTO_PARTICIPANTE`/`EVENTO_CANCELADO`).
- **afterCommit**: `inscricao.cancelada` publicado só após commit; rollback (409) não publica.

### Code review (Revisor, opus) — 0 P0/P1 (1 P1 aplicado)
- **CR-5B-01 (P1, aplicado):** publishers com `RabbitTemplate` opcional logavam a perda do evento (gatilho de reembolso/saga) em DEBUG → elevado a **WARN** com `inscricaoId/eventId`; novo `InscricaoCanceladaPublisherTest` (unit). Comportamento inalterado.
- Confirmados aplicados os padrões S4/5A: idempotência, afterCommit real (I/O fora da tx), lock pessimista, token constante-no-tempo, `TicketClient` com timeouts 2s/3s + fail-closed + sem SSRF/vazamento de token, reputação sem N+1.
- **CR-5B-02 (P2, devolvido):** corrida check-in-vs-cancelamento do mesmo ingresso é **last-writer-wins** no `ingressos.status` (não quebra dinheiro nem duplo check-in). `architecture.md` **alinhada ao comportamento real**; hardening (UPDATE condicional) previsto na **5C**. Demais P2/P3 → backlog. Detalhe em `memory/sprint-5/5b/code-review.md`.

### Testes
- **Local:** reactor `mvn verify` **SUCCESS** (backend 307 testes, 0 falhas) · frontend `npm ci && vite build && test:run` **97/97** · regressão S1–5A verde.
- **Integração (Testcontainers PG + RabbitMQ):** check-in/cancelamento concorrentes, reembolso individual idempotente, corrida individual-vs-massa, afterCommit, elegibilidade `participou` — **executados neste CI** (não rodam no Windows local). Conferir verdes antes do merge.

### DoD
- [x] Sem migration nova (tabelas `checkins`/`avaliacoes`/`reembolsos.motivo`/`processed_events` já cobrem a 5B).
- [x] Reactor compila + unit/regressão verdes (local).
- [ ] Suíte Testcontainers (concorrência/idempotência) verde **neste CI**.
- [x] Code review sem P0/P1 (1 P1 corrigido com teste).
- [x] Fluxos S1–5A intactos.

Não fazer merge automático — decisão humana.
