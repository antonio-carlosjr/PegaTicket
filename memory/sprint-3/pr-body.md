## Sprint 3 — Inscrição & Ingresso QR (gratuito)

Transforma o `ticket-service` de stub em serviço real: participante se inscreve em evento **gratuito**, recebe **ingresso único com QR**, e vê **"meus ingressos"** + histórico. O coração da sprint é **concorrência de abre-vendas** (sem overbooking, sem dupla inscrição). Pipeline SDD completo (planejamento → arquitetura → TDD → review adversarial).

### Histórias entregues (aceitas pelo PO)
- **US-030** — inscrição em evento gratuito via **mini-saga síncrona** (validar → reservar vaga → tx local → compensar).
- **US-031** — **controle de capacidade + sem dupla inscrição** (núcleo de concorrência).
- **US-032** — **ingresso único com QR** (`UNIQUE(inscricao_id)` + `codigo_unico` UUID v4; QR renderizado no front).
- **US-033** — **"meus ingressos"** + histórico paginado.

### O que muda
- **event-service:** endpoints internos `POST /internal/events/{id}/reservar-vaga|liberar-vaga` (decremento/incremento atômico) + `GET /internal/events/{id}` (resumo p/ validação) — todos sob `X-Internal-Token`, **não roteados pelo gateway** (ADR-T08). `EventRepository` com `UPDATE ... WHERE vagas>0` checando `rowsAffected`.
- **ticket-service:** `@Entity Inscricao`/`Ingresso`, `InscricaoService` (saga + compensação via `TransactionTemplate REQUIRES_NEW`), `EventClient` (RestClient, timeouts 2s/3s, erros tipados), `TicketController` (3 endpoints), `GlobalExceptionHandler`. `hibernate.jdbc.time_zone: UTC`.
- **Frontend:** botão "Inscrever-se" (só `GRATUITO`+`PUBLICADO`+vagas>0), tela de ingresso com **QR** (`qrcode.react`), "Meus ingressos", "Minhas inscrições" (histórico paginado) + nav/rotas.
- **Infra:** `docker-compose` liga ticket→event (`EVENT_SERVICE_URL`/`INTERNAL_TOKEN` + `depends_on`); `.env.example` documenta `INTERNAL_TOKEN`.

### Concorrência (ADR-T07) — declarada e **testada em Postgres real**
- **Overbooking:** decremento atômico `UPDATE eventos SET vagas_disponiveis = vagas_disponiveis - 1 WHERE id=? AND status=PUBLICADO AND vagas_disponiveis > 0` + `rowsAffected`; `CHECK (vagas >= 0)` como defesa em profundidade.
- **Dupla inscrição:** `UNIQUE(usuario_id, evento_id)` + captura de `DataIntegrityViolationException` → 409 `JA_INSCRITO`.
- **Falha parcial cross-service:** compensação (`liberar-vaga`) idempotente no teto; falha de compensação → log `[RECONCILIACAO]`.

### Validação
- **`mvnw verify`: BUILD SUCCESS** (event 78 / ticket 28 / user 25; Testcontainers de concorrência rodam no CI `ubuntu-latest`, pulam só local).
- **Smoke de concorrência em Postgres real: ✅ 14/14** — 20 inscrições paralelas na última vaga → exatamente 5 OK / 15 `EVENTO_ESGOTADO`, `vagas=0` nunca negativo; dupla inscrição concorrente → 1 OK / 1 `JA_INSCRITO`; defesa de roteamento + autorização interna.
- **Frontend:** build limpo; testes **60/60**.

### 3 bugs P1 achados na validação (Postgres real) e corrigidos
1. **Saga dava 503 em Docker** — `ticket-service` sem `EVENT_SERVICE_URL` (default `localhost` apontava pra si mesma). *(também quebraria na Railway)*
2. **Validação usava endpoint público** que exige `X-User-Id` — chamada interna só tem `X-Internal-Token` → 401→503. Fix: `GET /internal/events/{id}`.
3. **Rota inexistente dava 500** — catch-all engolia `NoResourceFoundException`. Fix: → 404 nos 3 serviços.

> Nenhum apareceu no `mvnw verify` (H2/mocks). Ver [`bugs.md`](../sprint-3/bugs.md).

### Code review (Revisor, opus) — `code-review.md`
- **P0 = 0 · P1 = 0 · P2 = 5 · P3 = 5 → PRONTO PARA PR.** Núcleo de concorrência confirmado correto (14 itens verificados: sem race, sem N+1 de banco, sem HTTP dentro de `@Transactional`, compensação não mascara exceção, canal interno seguro).
- **P2 aplicados:** token interno **timing-safe** (`MessageDigest.isEqual`) + DRY; log distinto de 403 (mis-config de token); `INTERNAL_TOKEN` no `.env.example`; limpeza de import/javadoc.
- **Diferidos (backlog):** `findById` extra no hot path (fix correto = `RETURNING`); dedupe do fan-out no front; refetch de vagas pós-inscrição.

### Definition of Done
- [x] ticket-service real (inscrição + ingresso QR + listagens); stubs 501 removidos.
- [x] event-service com `reservar/liberar-vaga` atômicos + `GET /internal/events/{id}`.
- [x] **Testes de concorrência verdes** (Testcontainers no CI + smoke integrado 14/14) — gate inegociável.
- [x] Front: inscrever + meus-ingressos (QR) + histórico, estados de UI.
- [x] `mvnw verify` + commits atômicos `tipo(US-id)`; code review aplicado.
- [x] ADRs P09/T07/T08/T09; backlog; retrospectiva.

> Caminho **pago** (escrow/saga AMQP) é a **Sprint 4**. Check-in/cancelamento são a **Sprint 5**.
