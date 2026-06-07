---
description: Desenvolve UMA estória/épico de um sprint dentro do pipeline (paraleliza devs)
argument-hint: <sprint> <US-id> (ex.: "1 US-031")
---

# /desenvolver-estoria $ARGUMENTS

> Adaptador do Claude Code. A fonte da verdade (tool-agnostic, versionada) é o runbook [`commands/desenvolver-estoria.md`](../../commands/desenvolver-estoria.md).

Você é o **orquestrador** do time SDD do Ticketeira. Execute o runbook [`commands/desenvolver-estoria.md`](../../commands/desenvolver-estoria.md) para `$ARGUMENTS` (sprint + ID da história), seguindo cada passo. Use [`workflows/pipeline.md`](../../workflows/pipeline.md) e os [`agents/`](../../agents/). Commits atômicos reais via DevOps, sem `Co-Authored-By`. Modelos: opus/sonnet, nunca haiku.
