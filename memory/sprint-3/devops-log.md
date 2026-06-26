# Sprint 3 — DevOps Log

## Fase 0 — Setup (2026-06-26)
- Base: `main @ b62c2aa` (Sprint 2 — Eventos — mergeada via PR #2).
- Branch: **`feat/sprint-3-inscricao-ingresso`**.
- Pré-requisitos OK: `00-sprint-spec.md` + `po-planning.md` (do `/planejar-sprint 3`).
- Housekeeping: marcar US-030..033 em SPRINT-3 e Sprint 2 (US-020..023) DONE no backlog.

## Lições aplicadas (Sprint 1+2)
- **Validar em Postgres real** (smoke), não só H2; cobrir o caminho-feliz.
- **ESTE é o sprint de concorrência** → o **teste de última vaga concorrente é gate inegociável** (DoD). Estratégia: decremento atômico `UPDATE ... SET vagas = vagas - 1 WHERE id=? AND vagas > 0` + checar `rowsAffected`; `UNIQUE(usuario_id, evento_id)` para dupla inscrição; compensação (`liberar-vaga`) na falha parcial cross-service.
- Regras de data/param já promovidas ao `coding-standards.md`.

## Commits (branch `feat/sprint-3-inscricao-ingresso`, 13 à frente da `main`)
| Hash | Commit |
|---|---|
| df0cef6 | docs(sprint-3): arquitetura, contratos, data-model, tests-spec + ADRs T07/T08/T09 |
| 880342d | feat(US-031): reservar/liberar-vaga atomico + endpoints internos (event-service) |
| 8a8c93c | feat(US-030): ticket-service real — inscricao (mini-saga) + ingresso QR + EventClient |
| 9b488da | test(sprint-3): testes ticket/event-service + concorrencia (Testcontainers) |
| c14a866 | feat(US-032): frontend inscricao + ingresso QR + meus-ingressos + historico |
| 51508dc | test(sprint-3): testes das telas de inscricao/ingressos (Vitest) |
| bad1fe3 | docs(sprint-3): po-validation, logs e handoffs |
| 908a03c | **fix(US-030):** wiring ticket→event no compose (EVENT_SERVICE_URL/INTERNAL_TOKEN) — BUG-S3-01 |
| c812666 | **fix(US-030):** valida evento via GET /internal/events/{id} (X-Internal-Token) — BUG-S3-02 |
| 71b4df2 | **fix(sprint-3):** rota inexistente → 404 (NoResourceFoundException), nao 500 — BUG-S3-03 |
| 4776714 | test(US-031): cobre GET /internal/events/{id} (403/200/404) |
| 330f4e5 | chore: ignora *.tsbuildinfo |
| 5020842 | docs(sprint-1): artefatos SDD da sprint 1 no blackboard versionado |

## Validação (Fase 6) — gate de concorrência ✅
- `mvnw verify`: **BUILD SUCCESS** (reactor inteiro). ticket 28 testes (4 Testcontainers skip local).
- **Smoke de concorrência em Postgres real: ✅ 14/14** (última vaga concorrente sem overbooking, dupla inscrição concorrente, defesa de roteamento ADR-T08). Ver [`test-report.md`](test-report.md).
- **3 bugs P1 achados e corrigidos** (só apareceram no Postgres real, não no H2/CI) — ver [`bugs.md`](bugs.md).
- Testcontainers de concorrência **rodam no CI** (`ubuntu-latest` tem Docker); o skip é só local. Não é falso-verde no gate de PR.
