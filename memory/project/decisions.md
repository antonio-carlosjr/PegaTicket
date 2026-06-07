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

## ADR-P06 — Expansão de escopo do Sprint 1
**Status:** Aceita. Inclusão de **US-052** (perfil completo de promotor: CPF, telefone, e-mail de contato, endereço, redes sociais) e **US-053** (ativar/inativar usuários), aprovada pelo dono do produto (Sprint 1 / 2026-06-07). Justificativa: US-052 é pré-requisito para que o admin (US-050) possa avaliar o promotor com informações suficientes; US-053 completa o ciclo de governança de acesso sem o qual a tela admin seria incompleta. Não são scope creep: ambas estão contidas no Épico D e no roadmap RF01. Referência: `memory/sprint-1/00-sprint-spec.md` §2.

## ADR-P07 — Modelo de papel base (role base para todos)
**Status:** Aceita (Sprint 1, pedido do dono). **PARTICIPANTE é a role base de todo usuário autenticado.** Um candidato a promotor é criado/mantido como PARTICIPANTE + uma solicitação `PerfilVerificado(PENDENTE)` — já usa a plataforma como usuário comum. A **aprovação** promove papel→PROMOTOR (e `verificado=true`); a **rejeição** mantém PARTICIPANTE + `motivo_rejeicao` e permite **reenviar**. Aprovação e rejeição **disparam e-mail** (rejeição inclui o motivo). `Usuario.novoPromotorPendente` passa a criar PARTICIPANTE. Inclui **US-054**. Ref: `memory/sprint-1/00-sprint-spec.md` §5.1.

## ADR-P08 — Escopo do Sprint 2 (Eventos)
**Status:** Aceita. Épico A US-020/021/022/023: event-service vira real (criar/editar/publicar/cancelar + listagem/detalhe). Avaliações (US-024/025) adiadas para a Sprint 5. Ref: `memory/sprint-2/00-sprint-spec.md`.

## ADR-P09 — Escopo do Sprint 3 (Inscrição gratuita + ingresso QR)
**Status:** Aceita. Épico B US-030/031/032/033: caminho-feliz **sem dinheiro**, com controle de concorrência (capacidade + dupla inscrição) e ingresso com QR. Caminho pago, check-in e cancelamento ficam para Sprints 4/5. Ref: `memory/sprint-3/00-sprint-spec.md`.

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

## ADR-T05 — Seed de admin dev-only
**Status:** Proposta (dívida, Sprint 1). A migration V3 semeia 1 admin (`admin@pegaticket.local`) com hash de senha **em claro no repo**, só para dev/demo. **Decisão:** tornar a credencial env-driven / processo de bootstrap seguro antes de produção (remover o seed ou parametrizar). Ref: `memory/sprint-1/00-sprint-spec.md` §4.

## ADR-T07 — Reserva atômica de vaga (anti-overbooking)
**Status:** Proposta (Sprint 2→3). `eventos.vagas_disponiveis` é preparado na Sprint 2 (inicializado = capacidade ao publicar) e consumido na Sprint 3 via decremento atômico `UPDATE eventos SET vagas_disponiveis = vagas_disponiveis - 1 WHERE id=? AND vagas_disponiveis > 0` (checar rowsAffected). Endpoints internos `reservar/liberar-vaga` no event-service.

## ADR-T08 — Autorização inter-serviço (ticket→event)
**Status:** Proposta (Sprint 3). Os endpoints `reservar/liberar-vaga` são chamados **service-to-service** (ticket→event) na rede interna do Docker e **não são roteados publicamente** pelo gateway. **Decisão MVP:** isolamento de rede + não-exposição; evoluir para segredo compartilhado/mTLS depois.

## ADR-T09 — `codigo_unico` do ingresso (QR)
**Status:** Proposta (Sprint 3). Estratégia do código do ingresso: UUID v4 vs HMAC-assinado. Assinado facilita validação anti-forja no **check-in** (Sprint 5). Decisão final do Arquiteto na Sprint 3. O frontend renderiza o QR a partir do código.

---

> Toda nova decisão estrutural tomada por um agente durante um sprint é registrada aqui (com referência ao sprint), e recorrências de code review viram regra em `coding-standards.md`.
