# Decisões (ADRs) — Pipeline SDD + Ticketeira

> Log de decisões do **time de agentes** e decisões de produto/arquitetura tomadas durante os sprints.
> ADRs de arquitetura **originais** do projeto vivem em [`docs/adr/`](../../docs/adr/) (0001 microsserviços, 0002 db-per-service, 0003 JWT, 0004 RabbitMQ, 0005 monorepo) — referencie-os, não duplique.
> Formato: ADR-Pxx (processo) / ADR-Txx (técnica). Status: Proposta | Aceita | Substituída.

---

## ADR-P01 — Pipeline Spec-Driven com time de agentes
**Status:** Aceita. O desenvolvimento usa 7 agentes (DevOps, PO, Arquiteto, Backend, Frontend, Tester, Revisor) coordenados pelo orquestrador, comunicando via `memory/sprint-<n>/`. Fluxo em [`workflows/pipeline.md`](../../workflows/pipeline.md).

## ADR-P02 — Regra de modelos
**Status:** Aceita. Arquiteto e Revisor → `opus` (decisões/críticas). PO, DevOps, Backend, Frontend, Tester → `sonnet`. **Nunca `haiku`.**

## ADR-P03 — Commits atômicos, Conventional, sem co-autoria
**Status:** Aceita. Cada unidade coesa = 1 commit (`tipo(escopo): assunto`). **Não** usar trailer `Co-Authored-By` (preferência do dono). DevOps padroniza. Detalhe em [`coding-standards.md`](../../rules/coding-standards.md) §4.

## ADR-P04 — Memória in-repo como blackboard
**Status:** Aceita. Artefatos do pipeline vivem em `memory/` (versionado), não na memória automática do Claude. Pull, não push.

## ADR-P05 — Gates de aprovação pausam o pipeline
**Status:** Aceita. `/desenvolver-sprint` para e pede aprovação humana em: PO planning, validação da arquitetura, e aceite final.

---

## Dívidas técnicas conhecidas (viram histórias/ADR quando endereçadas)

## ADR-T01 — Papel (role) não vai no JWT
**Status:** Proposta (dívida). Hoje o token carrega só `sub/email/verificado`; o gateway injeta `X-User-Id/Email/Verified`, **sem papel**. Autorização por papel (ex.: ADMIN) não é possível só com o header atual. **Decisão a tomar:** incluir `papel` como claim no JWT + header `X-User-Papel` no gateway (US-051). Até lá, endpoints ADMIN ficam marcados como dívida e o Revisor sinaliza.

## ADR-T02 — `PUT /users/{id}/verify` sem proteção
**Status:** Proposta (dívida). Endpoint flippa `verificado` sem checar ADMIN (`// TODO`), e não atualiza `perfis_verificados.status` (os métodos `aprovar()/rejeitar()` são código morto). **Decisão:** US-050 implementa a tela + endpoint protegido que usa `aprovar()/rejeitar()` e exige papel ADMIN (depende de ADR-T01).

## ADR-T03 — Whitelist do gateway por prefixo
**Status:** Proposta (dívida). `JwtAuthGlobalFilter` usa `startsWith`, casando prefixos demais (ex.: `/api/auth/register-x`). **Decisão:** trocar por match exato no próximo toque no gateway.

## ADR-T04 — Consumidores RabbitMQ não implementados
**Status:** Proposta (dívida). Topologia declarada (`definitions.json`), mas sem `@RabbitListener`/`RabbitTemplate`. **Decisão:** ao implementar (US-060), todo consumidor é idempotente via `processed_events(event_id)`; produtor publica em `afterCommit`.

---

> Toda nova decisão estrutural tomada por um agente durante um sprint é registrada aqui (com referência ao sprint), e recorrências de code review viram regra em `coding-standards.md`.
