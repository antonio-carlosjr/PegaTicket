# Pipeline de Desenvolvimento — Time de Agentes (SDD)

> Spec-Driven Development. **Orquestrador:** o Claude principal coordena os agentes via a tool `Agent`.
> **Canal de comunicação:** os arquivos em [`memory/sprint-<n>/`](../memory/) (pull, não push — cada agente lê o contexto da memória ao começar).
> **Regra de modelo:** Arquiteto e Revisor → `opus`; PO, DevOps, Backend, Frontend, Tester → `sonnet`. **NUNCA `haiku`.**

---

## O time

| Agente | Papel | Modelo | Definição |
|---|---|---|---|
| **DevOps** | Branch, commits atômicos, CI, PR, Docker | sonnet | [`agents/devops.md`](../agents/devops.md) |
| **PO** | Escopo, histórias, critérios de aceite, aceite | sonnet | [`agents/po.md`](../agents/po.md) |
| **Arquiteto** | Design, contratos, spec de testes, modelo de dados | opus | [`agents/arquiteto.md`](../agents/arquiteto.md) |
| **Backend** | Implementação Spring/Java (TDD) | sonnet | [`agents/backend.md`](../agents/backend.md) |
| **Frontend** | Implementação React/Vite | sonnet | [`agents/frontend.md`](../agents/frontend.md) |
| **Tester** | Testes automatizados, regressão, bugs | sonnet | [`agents/tester.md`](../agents/tester.md) |
| **Revisor** | Code review profundo (validação) | opus | [`agents/revisor.md`](../agents/revisor.md) |

---

## Fluxo de um sprint

```
 /planejar-sprint N
        │
        ▼ (ultra plan)  → memory/sprint-N/00-sprint-spec.md + po-planning.md
        │
 /desenvolver-sprint N
        │
   [0] DEVOPS ──► cria branch feat/sprint-N-<tema>, abre devops-log.md
        │
   [1] PO ──────► po-planning.md (histórias + critérios)            ⏸ GATE: você aprova
        │
   [2] ARQUITETO► architecture.md + api-contracts.md + data-model.md + tests-spec.md
        │
   [3] PO ──────► po-validation.md (arquitetura serve à história?)   ⏸ GATE: você aprova
        │
   [4] TESTER ──► escreve os testes (vermelhos) a partir de tests-spec.md  (TDD)
        │
   [5] BACK ║ FRONT (paralelo) ─► implementam até os testes ficarem verdes
        │        cada unidade = 1 commit atômico (DevOps padroniza)
        │        handoff-frontend.md (back→front) · handoff-tester.md (→tester)
        │
   [6] TESTER ──► roda tudo, test-report.md + bugs.md → loop até zerar P0/P1
        │
   [7] PO ──────► po-acceptance.md (cada história ✅/❌)              ⏸ GATE: você aprova
        │
 /validar-sprint N
        │
   [8] REVISOR ─► code-review.md (recursão infinita, race, O(n²), N+1, segurança) → fixes
        │
   [9] DEVOPS ──► ./mvnw verify + npm test verdes → abre PR (gh) + retrospective.md
```

---

## Fases (detalhe)

### Fase 0 — DevOps: branch + setup
- Garante working tree limpa, atualiza `main`, cria `feat/sprint-<n>-<tema>` (ou por estória).
- Cria `memory/sprint-<n>/` (placeholders) se não existir. Abre `devops-log.md`.
- **Nenhum trabalho começa antes da branch existir.**

### Fase 1 — PO: planning
Lê `backlog.md` + `mvp-requirements`/`architectural-plan`. Produz `po-planning.md` (histórias, critérios operacionais por persona, objetivo em 1 frase, fora-de-escopo). Move histórias BACKLOG→SPRINT em `backlog.md`.
**⏸ GATE:** orquestrador mostra o `po-planning.md` ao usuário e espera "aprovado".

### Fase 2 — Arquiteto: design + contratos
Produz `architecture.md` (modelo de dados delta, módulos, estratégias de concorrência/idempotência/segurança, riscos), `api-contracts.md` (endpoints + DTOs + erros tipados + eventos AMQP), `data-model.md` (migrations Flyway + entidades JPA), `tests-spec.md` (casos JUnit + Vitest, TDD). ADRs em `decisions.md` se houver decisão relevante.

### Fase 3 — PO: validação da arquitetura
`po-validation.md` (cobre as histórias? respeita escopo? algo complexo demais?). **⏸ GATE.** Sprint não avança sem aprovação.

### Fase 4 — Tester: testes primeiro (TDD)
A partir de `tests-spec.md` + `api-contracts.md`, escreve os testes (backend JUnit + frontend Vitest) **vermelhos**. DevOps commita `test:`. Isso fixa o contrato executável antes de Back/Front implementarem.

### Fase 5 — Backend ‖ Frontend (paralelo)
Invocados na **mesma mensagem** (múltiplos `Agent`). Implementam até os testes ficarem verdes, reusando código existente. Cada unidade coesa → **commit atômico** (DevOps padroniza a mensagem). Back sinaliza `handoff-frontend.md`; ambos sinalizam `handoff-tester.md`. Logs em `backend-log.md`/`frontend-log.md`.

### Fase 6 — Tester: validação + loop de bug
Roda back+front, escreve E2E/integração faltante, `test-report.md` + `bugs.md`. Bug P0/P1 → atribui ao owner → fix com teste de regressão → re-valida. Loop até zerar.

### Fase 7 — PO: aceite
`po-acceptance.md` (história ✅/❌ com motivo). Não aceitas voltam pro `backlog.md`. **⏸ GATE.**

### Fase 8-9 — Revisor + DevOps (via `/validar-sprint`)
Revisor faz code review profundo (`code-review.md`); fixes aplicados com teste. DevOps roda `./mvnw verify` + `npm test`, e ao verde abre o **PR** (`gh pr create`) e escreve `retrospective.md`.

---

## Artefatos por sprint (quem escreve → quem lê)

| Arquivo | Escreve | Lê |
|---|---|---|
| `00-sprint-spec.md` | Orquestrador (ultra plan) | todos |
| `po-planning.md` | PO | todos |
| `architecture.md` | Arquiteto | Back, Front, Tester, PO |
| `api-contracts.md` | Arquiteto (Back atualiza delta) | Back, Front, Tester |
| `data-model.md` | Arquiteto | Back |
| `tests-spec.md` | Arquiteto | Tester, Back |
| `po-validation.md` | PO | Arquiteto |
| `devops-log.md` | DevOps | todos |
| `backend-log.md` | Backend | todos |
| `frontend-log.md` | Frontend | todos |
| `handoff-frontend.md` | Backend → Frontend | Front, Tester |
| `handoff-tester.md` | Front/Back → Tester | Tester |
| `test-report.md` | Tester | PO, Revisor |
| `bugs.md` | Tester | Back, Front |
| `code-review.md` | Revisor | Back, Front, DevOps |
| `po-acceptance.md` | PO | todos |
| `retrospective.md` | DevOps + todos | todos |

**Memória de longo prazo** (`memory/project/`): `architectural-plan.md`, `backlog.md`, `decisions.md` (ADRs), `tech-notes.md`. **Code review acumulado**: `memory/code-review/`.

---

## Regras de comunicação

1. **Handoff sempre por arquivo.** Nada implícito — o próximo agente lê a memória.
2. **Toda decisão importante vira ADR** em `decisions.md`. Não confiar em memória de sessão.
3. **Conflito:** orquestrador media. PO tem palavra final em **escopo**; Arquiteto em **design técnico**.
4. **Pull, não push:** cada agente puxa o contexto da memória ao iniciar; não assume estado de sessão anterior.
5. **Commits atômicos reais** durante todo o pipeline (DevOps padrão; ver `coding-standards.md` §4). Sem `Co-Authored-By`.
6. **Gates pausam** — o orquestrador mostra o artefato e espera aprovação do usuário antes de seguir (planning, validação da arquitetura, aceite).

## Como o orquestrador opera (resumo)
`mark_chapter` → setup memória → invoca cada fase via `Agent` (modelo correto) → pausa nos gates → coordena DevOps para commitar a cada unidade → ao fim, `/validar-sprint` revisa e abre PR. Detalhe operacional em cada comando de `.claude/commands/`.
