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
**Status:** **Aceita** (Sprint 3 — Arquiteto). `eventos.vagas_disponiveis` é preparado na Sprint 2 (inicializado = capacidade ao publicar) e consumido na Sprint 3.

**Decisão definitiva (Sprint 3):**
- **Reservar:** `@Modifying @Query` JPQL `UPDATE Evento e SET e.vagasDisponiveis = e.vagasDisponiveis - 1 WHERE e.id = :id AND e.status = PUBLICADO AND e.vagasDisponiveis > 0`, retornando `int rowsAffected`. **1** = reservou; **0** = esgotado ou não-publicado (o service distingue 409/422/404 com um `findById` **só** no caminho frio rowsAffected=0). A cláusula `WHERE vagas > 0` adquire row lock no Postgres → serializa as concorrentes, sem janela entre checar e decrementar. **Sem retry, O(1) por inscrição.**
- **Liberar (compensação):** `UPDATE ... SET vagas = vagas + 1 WHERE id=:id AND status=PUBLICADO AND vagas < capacidade` (incremento **limitado pela capacidade**; no-op idempotente no teto). Não perfeitamente idempotente por reserva individual (sem token de reserva); o teto `< capacidade` é a salvaguarda.
- **Rejeitado `@Version` (optimistic):** no abre-vendas vira tempestade de retries O(n²) numa única linha quente. O `UPDATE...WHERE` atômico é a escolha correta para alta contenção.
- **Defesa em profundidade:** `CHECK (vagas_disponiveis >= 0)` (V2) recusa negativo mesmo em bug.
- **Gate de teste:** concorrência de última vaga (K threads → 1 sucesso, K-1×409, vagas=0) **em Postgres real** (Testcontainers/smoke Docker) — H2 não reproduz o row lock. Detalhe em `memory/sprint-3/architecture.md` §Estratégias críticas e `tests-spec.md` §A4.

## ADR-T08 — Autorização inter-serviço (ticket→event)
**Status:** **Aceita** (Sprint 3 — Arquiteto).

**Achado que muda a decisão:** o gateway tem a rota `events: Path=/api/events/** → event-service` com `StripPrefix=1`. Se os internos ficassem sob `/events/{id}/reservar-vaga`, **qualquer participante autenticado** poderia chamar `POST /api/events/{id}/reservar-vaga` e zerar `vagas_disponiveis` (DoS de vagas). O wildcard **roteia** — "não exposto no gateway" não era verdade automaticamente.

**Decisão definitiva (duas camadas):**
1. **Roteamento:** os internos vivem em prefixo dedicado **`/internal/events/{id}/reservar-vaga`** e `/liberar-vaga`. O gateway **não tem** rota `/api/internal/**` → tentativa externa = **404 no gateway** (nunca chega ao event-service). Como não ficam sob `/events/...`, nem o wildcard `/api/events/**` os alcança.
2. **Autorização:** os internos exigem `X-Internal-Token == ${INTERNAL_SHARED_SECRET}` (env, gitignored, placeholder em `.env.example`). Ausente/errado → **403**. O ticket-service injeta o header no `EventClient`. O gateway **não** injeta nem repassa `X-Internal-Token`.
- **Sem mTLS** no MVP (over-engineering acadêmico); fica como dívida.
- **Gate de teste:** `/api/internal/events/1/reservar-vaga` via gateway → 404; chamada direta sem token → 403 (`tests-spec.md` §C). Detalhe em `architecture.md` §Autorização inter-serviço.

## ADR-T09 — `codigo_unico` do ingresso (QR)
**Status:** **Aceita** (Sprint 3 — Arquiteto).

**Decisão definitiva: UUID v4** (`java.util.UUID.randomUUID()`, gerado no backend, persistido em `codigo_unico VARCHAR(64)`). **HMAC-assinado rejeitado** para o escopo atual.

Justificativa:
- 122 bits de entropia (CSPRNG) → não-forjável na prática; sem padrão sequencial a explorar.
- O **check-in da Sprint 5 será lookup server-side** (`SELECT WHERE codigo_unico=? AND status=ATIVO` + evento correto), **não** verificação criptográfica offline. Para lookup no banco, UUID basta — HMAC só agregaria valor se a validação fosse offline/anti-adulteração sem banco (não é requisito conhecido).
- HMAC agora = abstração precoce (gestão/rotação de chave, formato de payload).
- `UNIQUE(codigo_unico)` é a última linha de defesa contra colisão.
- **Impacto Sprint 5:** check-in faz lookup por `codigo_unico`, marca `ingressos.status=UTILIZADO` + cria `checkins` (`UNIQUE(ingresso_id)` impede duplo check-in). Migrar para HMAC no futuro **invalidaria QRs já emitidos** → por isso a decisão UUID é registrada como definitiva para o escopo atual.

**QR:** o **frontend** renderiza a imagem a partir da string `codigo_unico` (lib JS nova — `qrcode.react` recomendada, justificar em `frontend-log.md`). **Backend NÃO gera imagem.**

---

> Toda nova decisão estrutural tomada por um agente durante um sprint é registrada aqui (com referência ao sprint), e recorrências de code review viram regra em `coding-standards.md`.
