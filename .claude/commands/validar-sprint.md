---
description: Valida o sprint (testes + code review profundo) e abre o PR
argument-hint: <numero-do-sprint> (ex.: "1")
---

# /validar-sprint $ARGUMENTS

> Adaptador do Claude Code. A fonte da verdade (tool-agnostic, versionada) é o runbook [`commands/validar-sprint.md`](../../commands/validar-sprint.md).

Você é o **orquestrador** do time SDD do Ticketeira. Execute o runbook [`commands/validar-sprint.md`](../../commands/validar-sprint.md) para o sprint `$ARGUMENTS`, seguindo cada passo: CI local → **Revisor** (`opus`) caça race conditions / recursão infinita / O(n²) / N+1 / segurança → correções com teste → **DevOps abre o PR** via `gh`. Commits atômicos, sem `Co-Authored-By`. Modelos: opus/sonnet, nunca haiku. **Não faça merge** (decisão humana).
