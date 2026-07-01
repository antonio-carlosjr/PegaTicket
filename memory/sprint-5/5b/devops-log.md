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
| 2 | docs | arquitetura, contratos, modelo de dados e spec de testes (ADR-T14/T15/T16) | 3 |
| 3 | docs | validação do PO (APROVADO COM RESSALVAS) | 3 |
| 4 | test | suíte vermelha (check-in, cancelamento+reembolso individual, avaliação/reputação, participou) — Testcontainers singleton | 4 |
| 5 | feat | ticket: check-in QR + cancelamento+reembolso individual + canal participou (US-034/035/024) | 5 |
| 6 | feat | event: avaliação (1-5) + reputação (média/total) + TicketClient (US-024/025) | 5 |
| 7 | feat | payment: reembolso individual ao cancelar inscrição paga (US-035) | 5 |
| 8 | feat | frontend: telas de check-in, cancelar inscrição e avaliar evento | 5 |

## Fase 5 — implementação (VERDE)
- Reactor `./mvnw -B -ntp verify` → **BUILD SUCCESS** (7 módulos). Frontend **97/97** + `tsc` limpo.
- 4 defeitos caçados na verificação local e corrigidos **antes** do commit da implementação (ver `bugs.md`): publishers com RabbitTemplate opcional (38 context-errors), `CancelamentoControllerTest` sem `@Transactional` (REQUIRES_NEW vs seed não-commitado), handler `MissingServletRequestParameter` → 400.
- Totais: event 121 (11 skip), ticket 101 (22 skip), payment 53 (32 skip) — 0 fail/0 err. Skipped = Testcontainers (CI).

## Fase 6 — relatórios
- `test-report.md` + `bugs.md` escritos. P0/P1 remanescentes locais: **0**. Integração Testcontainers pendente de CI (padrão S4/5A).

## Fase 7 — aceite do PO
- `po-acceptance.md`: **ACEITO COM RESSALVAS** (condicional ao CI); US-025 aceite pleno. Aprovado pelo humano no gate.

## validar-sprint 5b (CI + Revisor + PR)
- Passo 1 CI local: reactor `verify` SUCCESS + frontend 97/97.
- Passo 2 Revisor (opus): `code-review.md` — APROVADO COM RESSALVAS (P0=0, P1=1 aplicado CR-5B-01, P2=3, P3=2). Fix commit `f509317` (WARN + teste) + `9a44362` (doc CR-5B-02).
- Passo 3 regressão: `regression-report.md` — backend 307 / frontend 97, 0 falhas.
- Passo 4 PR: **[#21](https://github.com/antonio-carlosjr/PegaTicket/pull/21)** (`feat/sprint-5-experiencia` → `main`). **Sem merge (decisão humana).**

## Commits (validar)
| # | Tipo | Assunto |
|---|---|---|
| 9 | fix | publisher WARN + InscricaoCanceladaPublisherTest (CR-5B-01/05) |
| 10 | docs | code-review + alinha architecture.md (CR-5B-02) |
| 11 | docs | regression-report (Passo 3) |
| 12 | docs | pr-body (Passo 4) |
