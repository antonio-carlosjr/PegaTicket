## Sprint 2 — Eventos (event-service)

Transforma o `event-service` de stub em serviço real e adiciona as telas de eventos no frontend. Pipeline SDD completo (planejamento → arquitetura → TDD → review).

### Histórias entregues (aceitas pelo PO)
- **US-020** — Promotor verificado cria evento (gratuito/pago) → RASCUNHO.
- **US-021** — Editar/publicar/cancelar com **máquina de estados** e **ownership**.
- **US-022** — Participante lista/busca eventos publicados (filtros `q`/`tipo`/`de`/`ate` + paginação).
- **US-023** — Detalhe do evento (datas no fuso do usuário; sem inscrição — Sprint 3).

### O que muda
- **Backend:** `@Entity Evento` (mapeada 1:1 ao schema), enums `TipoEvento`/`StatusEvento`, `EventService` (transições + ownership 404), `EventRepository` (listagem `CAST(:q AS string)`), `EventController` (7 endpoints, papel via `X-User-Papel`), DTOs `record` + Bean Validation, `GlobalExceptionHandler`, **migration `V2`** (`vagas_disponiveis`, `imagem_url`, índice parcial), `hibernate.jdbc.time_zone: UTC`.
- **Frontend:** criar/editar (wizard 3 etapas), meus eventos, lista pública c/ filtros, detalhe; rotas + nav role-aware (`PromotorRoute`).

### Riscos mitigados
- **Ownership:** não-owner → 404 (não vaza existência). **Authz:** participante → 403 (antes de tocar o banco).
- **Timezone:** UTC no banco, offset na borda.
- **Postgres:** query de listagem com `CAST(:q AS string)` (evita `lower(bytea)`); `@Entity` ⇿ schema (passa `ddl-auto: validate`).
- **Máquina de estados:** transição inválida → 409 tipado.

### Resultado do code review (Revisor, opus)
- **P1 (corrigido):** filtro de data da lista enviava valor sem offset → **500**. Fix: conversão ISO + teste.
- **P2 (corrigido):** param malformado (`tipo=FOO`, id não-numérico) → 500 em vez de **400**. Fix: handler de type-mismatch + 3 testes.
- **P2/P3 documentados (follow-up):** `@Valid` antes da checagem de papel (CR-003); `sort` não usado / sem `ORDER BY` determinístico (CR-006); `imagemUrl` sem allowlist de esquema (CR-005). Ver `memory/sprint-2/code-review.md`.
- **Concorrência** (sem `@Version` nas transições) **diferida para a Sprint 3** (ADR-T07) — sem corrupção nesta sprint.

### Validação
- **Reactor `mvnw verify`: 90/90 PASS** (event-service 57; user-service 25 → Sprint 1 não regrediu).
- **Smoke em Postgres real: 11/11** (criar→publicar→listar→detalhe; participante→403; promotor B→404).
- **Frontend:** `npm run build` limpo; testes 51/52 (1 falha **pré-existente da Sprint 1**, fora de escopo — bug do label "E-mail" duplicado, já em tarefa separada).

### Definition of Done
- [x] event-service real (CRUD + publicar/cancelar), stubs 501 removidos.
- [x] V2 aplicada; `ddl-auto: validate` ok (validado em Postgres).
- [x] Authz por papel + ownership testados.
- [x] Front: meus-eventos + criar/editar + lista + detalhe, estados de UI.
- [x] `mvnw verify` verde; commits atômicos; code review aplicado.
- [x] ADRs (P08, T07) registradas; backlog atualizado; retrospectiva.

> Concorrência pesada (reserva de vaga atômica) e inscrição são da **Sprint 3** (ADR-T07).
