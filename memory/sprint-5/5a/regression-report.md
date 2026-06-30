# Sprint 5 · Trilha 5A — Regressão final (validar-sprint)

> Após o code review (0 P0/P1). Testcontainers não rodam no Windows local → integração valida no CI do PR.

## Reactor (`./mvnw -B -ntp verify`) — BUILD SUCCESS (0 falhas)
- event/payment/ticket/user/gateway/common compilam; unit + regressão **S2/S3/S4 verdes**.
- Integração 5A (A3/A4, B2, C1, C2, D1/D2) **skipped local** (`disabledWithoutDocker`) → **CI**.

## Frontend — `vite build` OK · `npm run test:run` **80/80** · `tsc` limpo

## Concorrência / idempotência / fan-out (Testcontainers PG+Rabbit) — CONFIRMAÇÃO NO CI
- Repasse idempotente em massa (reentrega → 1×) · Reembolso em massa idempotente · Corrida repasse-vs-reembolso (1 vencedor) · Fan-out `evento.cancelado`/`evento.cancelado.ticket` (payment + ticket) · publish afterCommit (rollback não publica).

## Veredicto
**Verde local; PRONTO PARA PR.** Invariantes da saga financeira a confirmar pelo CI do PR.
