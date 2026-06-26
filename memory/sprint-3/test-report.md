# Sprint 3 — Test Report (Inscrição & Ingresso QR)

> Fase 6 do pipeline. Validação rodada contra **Postgres real** (Docker Compose), não só H2 — lição transversal das Sprints 1–2 (H2 mascara bugs de runtime).

## 1. Reactor (`./mvnw -B -ntp verify`, JDK 21)

**BUILD SUCCESS** — reactor inteiro (common-lib, api-gateway, user/event/ticket/payment-service).

| Módulo | Tests run | Falhas | Erros | Skipped |
|---|---|---|---|---|
| ticket-service | 28 | 0 | 0 | **4** (Testcontainers — ver §3) |
| event-service | (verde) | 0 | 0 | (Testcontainers de repositório skip local) |
| user-service | 25 | 0 | 0 | 0 |
| payment/gateway/common | verdes | 0 | 0 | 0 |

> Mocks: os testes de controller/service do ticket **mockam `EventClient`** (Mockito), então a troca de URL interna (`/events/{id}` → `/internal/events/{id}`) **não os afeta**.

## 2. Gate inegociável — Smoke de CONCORRÊNCIA (Postgres real, requisições paralelas)

Script: `scratchpad/concurrency-smoke.sh` (20 inscrições paralelas + dupla inscrição concorrente + defesa de roteamento). **Resultado: ✅ 14/14 asserts.**

**T1 — Última vaga concorrente (sem overbooking)** · evento GRATUITO/PUBLICADO cap=5, **20 POSTs simultâneos**:
- exatamente **5** sucessos (201) = capacidade · **15** recusas **409 `EVENTO_ESGOTADO`**
- `vagas_disponiveis` final = **0**, **nunca negativo** (decremento atômico `UPDATE ... WHERE vagas>0` + `CHECK chk_vagas_nao_neg`)
- **5** inscrições `ATIVA` + **5** ingressos com `codigo_unico` distinto.

**T2 — Dupla inscrição concorrente** · mesmo usuário, **2 POSTs simultâneos** (cap=10):
- exatamente **1** inscrição criada · 2ª recusada **409 `JA_INSCRITO`** (`UNIQUE(usuario_id,evento_id)`)
- **1** linha persistida (sem duplicata) · **1** vaga debitada (`vagas=9`) → **compensação não vazou vaga**.

**T3 — Defesa de roteamento + autorização interna (ADR-T08)**:
- `POST /api/internal/events/{id}/reservar-vaga` → **404** (gateway não roteia `/api/internal/**`).
- `POST /api/events/{id}/reservar-vaga` (c/ token de usuário) → **404** (endpoint interno não vive sob `/events`).
- `POST /internal/...` direto **sem** `X-Internal-Token` → **403 `ACESSO_INTERNO_NEGADO`**.
- `POST /internal/...` direto com token **errado** → **403** (anti-spoof).

## 3. Testcontainers — por que pulou local e por que NÃO é falso-verde no CI

- `InscricaoConcorrenciaTest` (ticket) e `VagaConcorrenciaTest` (event) usam `@Testcontainers(disabledWithoutDocker = true)`.
- **Local (este Windows):** o Maven-JVM não alcança o daemon do Docker Desktop → JUnit **pula gracioso** (os 4 `Skipped`).
- **CI:** `backend.yml` roda em **`ubuntu-latest`** com `mvn verify` **sem** `-DexcludedGroups` → Docker disponível → **os testes de concorrência EXECUTAM no runner**. O gate de concorrência é real no CI.
- **Defesa em profundidade:** além do CI, a concorrência integrada (cross-service, ponta a ponta pelo gateway) foi provada **manualmente** aqui (§2) — cobertura que os testes unitários (com `EventClient` mockado / repositório isolado) não alcançam.

## 4. Frontend (Vitest)

- Inalterado nesta fase de validação (correções foram em compose + backend). Status da Fase 5 mantido: `npm run build` limpo; suíte verde (1 falha **pré-existente da Sprint 1** — label "E-mail" duplicado, já em tarefa separada, fora de escopo).

## 5. Veredito

**Gate de concorrência (DoD inegociável da Sprint 3): ✅ APROVADO.** Sem overbooking, sem dupla inscrição, compensação sem vazamento, endpoints internos blindados. P0/P1 zerados (3 bugs achados e corrigidos — ver [`bugs.md`](bugs.md)).
