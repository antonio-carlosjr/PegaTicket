# Comando: desenvolver-estoria `<n> <US-id>`

> Runbook **versionado e tool-agnostic**. Desenvolve **uma** estória/épico de um sprint dentro do pipeline — útil para paralelizar devs. Lógica das fases em [`workflows/pipeline.md`](../workflows/pipeline.md).

**Executor:** orquestrador.
**Objetivo:** entregar uma fatia (estória) com a mesma qualidade do pipeline completo.
**Entrada:** sprint + ID da história (ex.: `1 US-031`).
**Pré-requisitos:** o sprint já foi desenhado — existem `architecture.md`, `api-contracts.md`, `tests-spec.md`. Se não, rode `desenvolver-sprint <n>` até a Fase 3 primeiro.
**Referências:** [`workflows/pipeline.md`](../workflows/pipeline.md), [`agents/`](../agents/).

## Regras
Modelos `opus`/`sonnet` (Arquiteto/Revisor opus), nunca `haiku`. Commits atômicos reais via DevOps, sem `Co-Authored-By`. Comunicação por arquivo.

## Passos
1. **DevOps** — criar `feat/sprint-<n>/<US-id>-<slug>` (a partir da branch do sprint ou de `main` se isolada); registrar em `devops-log.md`; working tree limpa.
2. **Recorte do contrato** — releia só a parte de `architecture.md`/`api-contracts.md`/`tests-spec.md` que cobre `<US-id>`. Se a história não estiver coberta, acione o **Arquiteto** (`opus`) para o delta e o **PO** para validar. ⏸ Se mudou contrato, mostre ao usuário.
3. **Tester** — testes **vermelhos** da estória (incl. concorrência/auth se aplicável). DevOps commita `test:`.
4. **Backend e/ou Frontend** — implementam até verde, reusando código. Cada unidade → **DevOps commita** (`feat(escopo): <US-id> ...`). Atualizar logs + handoffs.
5. **Tester** — rodar os testes afetados; `test-report.md` (seção da estória) + `bugs.md`; loop de bug até zerar P0/P1 da estória.
6. **Entrega** — resumo: branch, commits, estado dos testes; dizer se integra à branch do sprint ou vira PR próprio. **Não abrir PR aqui.**

## Paralelismo entre devs (dividir carga)
Dois (ou mais) devs tocam estórias **independentes** do mesmo sprint em paralelo, cada um na sua branch:

```
# Dev A
/desenvolver-estoria 3 US-031     # capacidade + concorrência  → feat/sprint-3/US-031-capacidade
# Dev B (ao mesmo tempo)
/desenvolver-estoria 3 US-033     # meus ingressos            → feat/sprint-3/US-033-meus-ingressos
```

Cada branch integra à branch do sprint (`feat/sprint-<n>-...`) ao ficar verde; o **DevOps** coordena a ordem de merge e resolve conflitos. Estórias com **dependência** (ex.: US-032 depende de US-030) **não** paralelizam — rode em sequência. Quem não usa Claude Code abre este runbook e segue os passos manualmente para a sua estória.

## Saídas
Código + testes da estória; `backend-log.md`/`frontend-log.md`/handoffs atualizados; seção no `test-report.md`.

> Estórias independentes podem rodar em branches separadas e ser integradas à branch do sprint conforme ficam verdes. Conflitos → DevOps coordena a ordem de merge.
