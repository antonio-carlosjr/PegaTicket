# Comando: planejar-sprint `<n|tema>`

> Runbook **versionado e tool-agnostic**. Pode ser executado pelo Claude (via `/planejar-sprint`), por outra IA, ou seguido manualmente por um dev.

**Executor:** orquestrador (quem coordena o time).
**Objetivo:** planejamento profundo (ultra-plan) de um sprint → spec mestre + planning do PO.
**Entrada:** número ou tema do sprint.
**Referências:** [`workflows/pipeline.md`](../workflows/pipeline.md) (Fases 0-1), [`agents/po.md`](../agents/po.md), [`memory/project/architectural-plan.md`](../memory/project/architectural-plan.md), [`backlog.md`](../memory/project/backlog.md), [`decisions.md`](../memory/project/decisions.md), [`rules/coding-standards.md`](../rules/coding-standards.md).
**Pré-requisitos:** nenhum.

## Passos
1. **Contexto** — leia `architectural-plan.md`, `backlog.md`, `decisions.md`, `coding-standards.md`; e o contexto real do código: `docs/api/*.yaml`, os schemas Flyway dos serviços-alvo, e o `user-service` como gabarito de qualidade.
2. **Ultra-plan** (pense fundo e honesto): objetivo em 1 frase (ancorado no roadmap RF/RNF); escopo (épicos/histórias candidatas e o porquê de caberem em ~2 semanas); serviços afetados + delta de modelo de dados (migrations Flyway); endpoints e eventos AMQP previstos; **pontos de concorrência** (inscrição, capacidade, pagamento, emissão) + estratégia candidata; dependências entre histórias; riscos; dívidas tocadas (ex.: papel fora do token) e contorno; fora-de-escopo intencional; critérios de sucesso verificáveis. Se algo for ambíguo, faça 1-3 perguntas objetivas antes de fechar.
3. **Grave a spec mestre** em `memory/sprint-<n>/00-sprint-spec.md` (estruturado, com tabelas).
4. **Acione o PO** (modelo `sonnet`): "Leia `agents/po.md` + `00-sprint-spec.md` + `backlog.md`. Gere `memory/sprint-<n>/po-planning.md` (histórias 'Como <ator> quero...', critérios operacionais por ator Bruno/Marina/Admin incl. cenário de concorrência, fora-de-escopo). Mova as histórias para SPRINT-<n> no `backlog.md`."
5. **Entregue** ao usuário: objetivo, histórias, riscos, fora-de-escopo, arquivos criados. Peça aprovação do escopo. **Não desenvolva aqui** (isso é `desenvolver-sprint`).

## Saídas
`memory/sprint-<n>/00-sprint-spec.md`, `po-planning.md`; `backlog.md` atualizado.

## Regras
Modelos só `opus`/`sonnet` (Arquiteto/Revisor opus; resto sonnet), **nunca `haiku`**. Decisão estrutural → ADR em `decisions.md`.
