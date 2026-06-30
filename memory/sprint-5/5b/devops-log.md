# Sprint 5 · Trilha 5B (experiência) — DevOps log

Branch: `feat/sprint-5-experiencia` (base: `main` @ 01dab1b — pós-merge da 5A).
Escopo (ADR-P11, faseado): **US-034** (check-in por QR na porta) + **US-035** (cancelar inscrição c/ política de prazo; se pago → reembolso individual `CANCELAMENTO_PARTICIPANTE` reusando o mecanismo da 5A) + **US-024** (avaliar evento, nota 1-5) + **US-025** (reputação = média/total).

Fora desta trilha: 5C (carga/observabilidade/hardening).

## Fase 0 — setup
- [x] Branch criada de `main` (5A mergeada — mecanismo de reembolso disponível).
- [x] `memory/sprint-5/5b/` aberto.
- [x] ADR-P11 atualizado (5A mergeada; 5B em desenvolvimento).
- Working tree: `docs/poster/` e `frontend/.gitignore` permanecem não rastreados (fora do escopo).

## Schemas já existentes (5B mexe pouco no modelo)
- ticket: `checkins(ingresso_id UNIQUE)` + `ingressos.status` UTILIZADO → check-in (US-034); `inscricoes.status` CANCELADA → cancelamento (US-035).
- event: `avaliacoes(nota 1-5, UNIQUE evento+usuario)` + `prazo_reembolso_dias` → avaliação (US-024/025) + política de prazo (US-035).

## Convenções
- Conventional Commits, **sem `Co-Authored-By`** (ADR-P03). `git add` por caminho (nunca `-A`).
- Reusa padrões S4/5A: idempotência `processed_events` + publish `afterCommit` (ADR-T11), token interno (ADR-T08), **`TestcontainersBase` singleton + purge de filas no @BeforeEach** (coding-standards §Testes), money `setScale(2)`+`@JsonFormat STRING`.

## Decisões de produto a fechar (Fase 1/3 — PO/Arquiteto)
- Elegibilidade de avaliação (PO-D1 do planning: ingresso UTILIZADO **ou** inscrição ATIVA em evento REALIZADO).
- Política de prazo de reembolso (PO-D2: fora do prazo → bloqueia com 422).
- Autorização do check-in (PROMOTOR + dono do evento).

## Commits
| # | Tipo | Assunto | Fase |
|---|---|---|---|
| 1 | docs | inicia trilha 5B (ADR-P11 atualizado; devops-log) | 0 |
