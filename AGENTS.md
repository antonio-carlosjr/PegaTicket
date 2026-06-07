# Time de Agentes & Pipeline SDD — Ticketeira

Sistema de **desenvolvimento orientado a especificações (Spec-Driven Development)** com um time de 7 agentes coordenados pelo Claude. Inspirado na estrutura do SaloonBarber, adaptado à stack do Ticketeira (Spring Boot/Java 21 microsserviços + React/Vite) e com **slash commands** de verdade.

## O time

| Agente | Papel | Modelo | Arquivo |
|---|---|---|---|
| 🔧 **DevOps** | Branch, commits atômicos, CI, PR | sonnet | [`agents/devops.md`](agents/devops.md) |
| 📋 **PO** | Escopo, histórias, critérios, aceite | sonnet | [`agents/po.md`](agents/po.md) |
| 🏛️ **Arquiteto** | Design, contratos, spec de testes | opus | [`agents/arquiteto.md`](agents/arquiteto.md) |
| ⚙️ **Backend** | Spring/Java (TDD) | sonnet | [`agents/backend.md`](agents/backend.md) |
| 🎨 **Frontend** | React/Vite | sonnet | [`agents/frontend.md`](agents/frontend.md) |
| 🧪 **Tester** | Testes automatizados, regressão | sonnet | [`agents/tester.md`](agents/tester.md) |
| 🔍 **Revisor** | Code review profundo | opus | [`agents/revisor.md`](agents/revisor.md) |

> Regra de modelo: Arquiteto e Revisor = `opus`; o resto = `sonnet`. **Nunca `haiku`.**

## Os comandos

| Comando | O que faz |
|---|---|
| `/planejar-sprint <n>` | Ultra-plan do sprint → grava `00-sprint-spec.md` + aciona o PO (`po-planning.md`) |
| `/desenvolver-sprint <n>` | Roda o pipeline: DevOps (branch) → PO → Arquiteto (contratos) → ⏸ PO valida → Tester (testes vermelhos) → Back‖Front → Tester (loop de bug) → ⏸ PO aceita. **Commits atômicos reais.** |
| `/desenvolver-estoria <n> <US-id>` | Mesma pipeline para **uma** estória/épico (paraleliza devs) |
| `/validar-sprint <n>` | Roda todos os testes → **Revisor** (recursão infinita, race conditions, O(n²), N+1, segurança) → corrige → abre **PR** via `gh` |

⏸ = gate que **pausa** e pede sua aprovação.

> **Os comandos são versionados e tool-agnostic** em [`commands/`](commands/) — runbooks que qualquer dev ou IA pode seguir, **sem depender do Claude Code**. Em [`.claude/commands/`](.claude/commands/) ficam apenas **adaptadores finos** que oferecem o atalho `/comando` no Claude Code apontando para esses runbooks. Quem não usa Claude Code abre o arquivo em `commands/` e segue os passos.

## O fluxo

```
/planejar-sprint 1                /desenvolver-sprint 1                       /validar-sprint 1
      │                                  │                                          │
  ultra-plan ──► 00-sprint-spec    DevOps branch ──► PO ──► Arquiteto ──► ⏸PO   Revisor ──► fixes ──► DevOps ──► PR
                 po-planning                          contratos    Tester(TDD)               (race/O(n²)/
                                                      Back ‖ Front  Tester(loop)               N+1/segurança)
                                                      commits atômicos   ⏸PO aceite
```

## Memória (o blackboard — `memory/`)

```
memory/
├── project/          # durável
│   ├── architectural-plan.md   # ⭐ a verdade da arquitetura/stack (todos leem)
│   ├── backlog.md              # histórias (épicos A-E)
│   └── decisions.md            # ADRs do pipeline + dívidas técnicas
├── sprint-<n>/       # por sprint (criado pelos comandos)
│   ├── 00-sprint-spec.md  po-planning.md  architecture.md  api-contracts.md
│   ├── data-model.md  tests-spec.md  po-validation.md  devops-log.md
│   ├── backend-log.md  frontend-log.md  handoff-frontend.md  handoff-tester.md
│   ├── test-report.md  bugs.md  code-review.md  po-acceptance.md  retrospective.md
└── code-review/      # aprendizados acumulados que viram regra
```

Regras de engenharia: [`rules/coding-standards.md`](rules/coding-standards.md). Orquestração: [`workflows/pipeline.md`](workflows/pipeline.md).

## Quickstart

```
1) /planejar-sprint 1          # planeja, você aprova o escopo
2) /desenvolver-sprint 1       # roda o pipeline; aprova nos gates (planning, arquitetura, aceite)
3) /validar-sprint 1           # code review + CI + abre o PR
```

## Princípios

- **Pull, não push:** cada agente lê o contexto da memória; nada implícito.
- **Spec antes do código:** contratos e testes (vermelhos) antes da implementação (TDD).
- **Commit atômico** por unidade coesa (Conventional Commits, sem `Co-Authored-By`).
- **Concorrência é cidadã de primeira classe:** toda mutação de risco (inscrição, capacidade, pagamento) declara e testa sua estratégia.
- **Gates humanos** nos pontos de decisão. Você manda.
