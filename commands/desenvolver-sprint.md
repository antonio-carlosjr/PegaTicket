# Comando: desenvolver-sprint `<n>`

> Toda a lógica das fases está em [`workflows/pipeline.md`](../workflows/pipeline.md) — este arquivo só orquestra a ordem e os gates.

**Executor:** orquestrador.
**Objetivo:** rodar o pipeline SDD completo de um sprint, com commits atômicos reais.
**Entrada:** número do sprint.
**Pré-requisitos:** `memory/sprint-<n>/00-sprint-spec.md` e `po-planning.md` já existem (rode `planejar-sprint` antes). Se não existirem, pare e avise.
**Referências:** [`workflows/pipeline.md`](../workflows/pipeline.md) (fonte da verdade das fases), [`agents/`](../agents/), [`rules/coding-standards.md`](../rules/coding-standards.md) (commits §4).

## Regras válidas em todo o pipeline
- Modelos: Arquiteto e Revisor = `opus`; PO, DevOps, Backend, Frontend, Tester = `sonnet`. **Nunca `haiku`.**
- Comunicação **por arquivo** em `memory/sprint-<n>/` (pull, não push). Cada agente lê seu `agents/<papel>.md` + os inputs.
- **Commits atômicos reais**: a cada unidade coesa, o **DevOps** commita (Conventional Commits, **sem `Co-Authored-By`**).
- **Gates pausam** (⏸): mostre o artefato e espere aprovação do usuário.

## Passos (executa as fases de `workflows/pipeline.md`)
0. **DevOps** — `mark_chapter` "Sprint <n>"; atualizar `main`; criar `feat/sprint-<n>-<tema>`; preparar `memory/sprint-<n>/`; abrir `devops-log.md`. **Nada começa sem branch.**
1. **PO** — confirmar/ajustar `po-planning.md`. **⏸ GATE** (objetivo + histórias).
2. **Arquiteto** (`opus`) — `architecture.md` + `api-contracts.md` + `data-model.md` + `tests-spec.md` (+ ADRs). DevOps commita `docs:`.
3. **PO** — `po-validation.md`. **⏸ GATE** (APROVADO obrigatório; senão volta à 2).
4. **Tester** — escrever os testes **vermelhos** (TDD) de `tests-spec.md`, incl. concorrência + auth-boundary. DevOps commita `test:`.
5. **Backend ‖ Frontend** (paralelo, mesma mensagem) — implementam até verde, reusando código. Cada unidade coesa → **DevOps commita** (`feat(escopo): ...`). Handoffs + logs.
6. **Tester** — rodar tudo (`./mvnw -pl ... test` + `npm run test:run`), `test-report.md` + `bugs.md`. Loop de bug (owner corrige com teste de regressão → DevOps `fix:` → re-valida) até zerar P0/P1.
7. **PO** — `po-acceptance.md` (história ✅/❌). **⏸ GATE.** Não aceitas voltam ao `backlog.md`.

## Encerramento
Resuma: branch, commits (do `devops-log.md`), histórias aceitas, pendências. Próximo passo: **`validar-sprint <n>`** (code review + PR). **Não abra PR aqui.**

> Para entregar só uma fatia (paralelismo de devs), use `desenvolver-estoria`.
