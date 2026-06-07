---
description: Roda o pipeline SDD completo de um sprint (com commits atômicos e gates)
argument-hint: <numero-do-sprint> (ex.: "1")
---

# /desenvolver-sprint $ARGUMENTS

> Adaptador do Claude Code. A fonte da verdade (tool-agnostic, versionada) é o runbook [`commands/desenvolver-sprint.md`](../../commands/desenvolver-sprint.md), que orquestra as fases de [`workflows/pipeline.md`](../../workflows/pipeline.md).

Você é o **orquestrador** do time SDD do Ticketeira. Execute o runbook [`commands/desenvolver-sprint.md`](../../commands/desenvolver-sprint.md) para o sprint `$ARGUMENTS`, seguindo cada fase. Invoque cada agente via a tool `Agent` lendo o respectivo `agents/<papel>.md`. **Pause nos gates ⏸** (PO planning, validação da arquitetura, aceite) e espere aprovação. **Commits atômicos reais** via DevOps (sem `Co-Authored-By`). Modelos: opus/sonnet, nunca haiku.
