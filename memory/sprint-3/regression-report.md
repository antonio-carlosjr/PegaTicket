# Sprint 3 — Regressão final (pré-PR)

> Tester. Reconfirmação de tudo verde após aplicar os fixes do code-review (CR-S3-01/04/05/09).

## Backend — `./mvnw -B -ntp verify`
**BUILD SUCCESS** (reactor inteiro).
- **event-service:** 78 testes · 0 falha · 8 skipped (`VagaConcorrenciaTest` Testcontainers — skip local, roda no CI). `InternalEventControllerTest` **13/13** (inclui os 3 do `GET /internal/events/{id}` + valida o refactor `tokenValido` timing-safe).
- **ticket-service:** 28 testes · 0 falha · 4 skipped (`InscricaoConcorrenciaTest` Testcontainers).
- **user-service:** 25 · 0 falha. payment/gateway/common verdes.

## Frontend — `npm run build` + `npm run test:run`
- build ✓ (warning de chunk >500kB é pré-existente, não bloqueia).
- **test:run: 60/60** — suíte 100% verde (corrigido o regex de label `E-mail` que casava `E-mail de Contato`).

## Concorrência (gate inegociável) — smoke em Postgres real
**✅ 14/14** (reconfirmado): última vaga concorrente (5/5 sem overbooking), dupla inscrição concorrente (1 sucesso/1 `JA_INSCRITO`), defesa de roteamento + autorização interna (ADR-T08). Ver [`test-report.md`](test-report.md).

## Veredito
**Sem P0/P1. Regressão limpa. Pronto para PR.**
