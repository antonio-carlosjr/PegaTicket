# Sprint 5 · Trilha 5A (financeiro) — DevOps log

Branch: `feat/sprint-5-financeiro` (base: `main` @ 6d1053d — pós-merge do Sprint 4).
Escopo (ADR-P11, faseado): **US-043** (repasse −10% pós-evento REALIZADO) + **US-042** (reembolso por evento cancelado) + wiring `evento.finalizado` (existente) e `evento.cancelado` (nova fila) + transições de status do evento + **TECH-S4-01** (`evento_id`/`promotor_id` em `pagamentos`).

Fora desta trilha: 5B (check-in/cancelamento/avaliações), 5C (carga/observabilidade/hardening).

## Fase 0 — setup
- [x] Branch criada de `main` atualizado (S4 mergeado).
- [x] `memory/sprint-5/5a/` aberto.
- [x] ADR-P11 → Aceita (faseado, 5A em execução).
- Working tree: `docs/poster/` e `frontend/.gitignore` permanecem não rastreados (fora do escopo).

## Convenções
- Conventional Commits, **sem `Co-Authored-By`** (ADR-P03). `git add` por caminho (nunca `-A`).
- Reusa os padrões do S4: idempotência `processed_events` + publish `afterCommit` (ADR-T11), reserva atômica (ADR-T07), token interno (ADR-T08), consumidor trata "evento tarde demais" com ACK no-op (CR-S4-01).

## Commits
| # | Tipo | Assunto | Fase |
|---|---|---|---|
| 1 | docs | planejamento da trilha 5A + ADR-P11 aceito (faseado) | 0 |
