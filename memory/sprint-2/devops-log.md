# Sprint 2 — DevOps Log

## Fase 0 — Setup (2026-06-26)
- Base: `main @ 3405269` (Sprint 1 mergeada, CI verde).
- Branch criada: **`feat/sprint-2-eventos`**.
- Pré-requisitos OK: `00-sprint-spec.md` + `po-planning.md` já existem (do `/planejar-sprint 2`).
- Pendência de housekeeping: marcar US-050..054 (Sprint 1) como DONE no `backlog.md` (no 1º commit de docs).

## Aprendizado herdado da Sprint 1 (aplicar nesta sprint)
- **H2 ≠ Postgres** foi a fonte de 4 bugs de runtime escondidos do CI (`uf CHAR(2)`, `lower(bytea)`, etc.). Nesta sprint: validar `event-service` rodando em **Postgres real** (Docker), não só `mvn verify` (H2); cobrir o **caminho-feliz** (não só os 403); cuidado com **paginação/queries** Postgres-específicas.

## Commits
| # | Tipo | Fase | Hash |
|---|---|---|---|
| (a preencher conforme o pipeline avança) | | | |

## Fase 6 — QA / Regressão (2026-06-26)
- Reactor Maven `mvnw -B -ntp verify`: **87/87 PASS**, BUILD SUCCESS (1m10s).
- Frontend `npm run test:run`: **50/51 PASS** — 1 falha P3 pré-existente Sprint 1 (`Register.test.tsx`).
- Frontend `npm run build`: zero erros de tipo, built em 1.86s.
- Smoke Postgres (11/11 documentado em `test-report.md`): Flyway V2 OK, `ddl-auto: validate` OK, `lower(bytea)` não se repete.
- Relatórios criados: `test-report.md`, `bugs.md`, `regression-report.md`.
- **Veredito: APROVADO PARA PO.**
