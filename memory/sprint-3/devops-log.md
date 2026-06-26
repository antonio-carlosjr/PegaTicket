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

## Commits
| # | Tipo | Fase | Hash |
|---|---|---|---|
| (a preencher) | | | |
