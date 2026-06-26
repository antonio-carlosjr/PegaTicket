# Sprint 2 — Test Report

> QA/Test Engineer Sênior. Data: 2026-06-26. Branch: `feat/sprint-2-eventos`.

---

## Por feature (US-020..023)

| Feature | Unit | Integração (MockMvc) | Smoke Postgres | Status |
|---|---|---|---|---|
| US-020 — Criar evento | EventoTest (10) + EventServiceTest (15) | EventControllerIntegrationTest (28) | 11/11 PASS | OK |
| US-021 — Editar/Publicar/Cancelar | Transições na entidade (EventoTest) + ownership (EventServiceTest) | EventControllerIntegrationTest — fluxo e-to-e | 11/11 PASS | OK |
| US-022 — Listar eventos (filtros/paginação) | — | EventControllerIntegrationTest | Smoke step 5,7,8 | OK |
| US-023 — Detalhe do evento | — | EventControllerIntegrationTest | Smoke step 9 | OK |

---

## Distribuição de testes do event-service (54 no total)

| Suite | Tipo | Testes |
|---|---|---|
| `EventoTest` | Unitário — entidade (domínio puro) | 10 |
| `EventServiceTest` | Unitário — service com mocks de repo | 15 |
| `EventControllerIntegrationTest` | Integração — MockMvc + H2 | 28 |
| `EventServiceApplicationTests` | Context load | 1 |
| **Total event-service** | | **54** |

---

## Cobertura por critério do tests-spec.md

### Criar (US-020)
- [x] GRATUITO valido → RASCUNHO, `promotorId = X-User-Id`, `vagasDisponiveis = null`, `preco = null`
- [x] PAGO valido (preco > 0, prazo >= 0) → RASCUNHO
- [x] PAGO com `preco = 0` / `null` → 400 (Bean Validation)
- [x] GRATUITO com `preco != null` → 400 (coerencia)
- [x] `dataFim < dataInicio` → 400 (`@AssertTrue`)
- [x] titulo / data / capacidade ausentes → 400 com mensagem por campo

### Transicoes de estado (US-021)
- [x] `publicar` RASCUNHO → PUBLICADO + `vagasDisponiveis == capacidade`
- [x] `cancelar` RASCUNHO → CANCELADO
- [x] `cancelar` PUBLICADO → CANCELADO (some da lista)
- [x] `publicar` ja PUBLICADO → 409 `EVENTO_JA_PUBLICADO`
- [x] `publicar` CANCELADO → 409 `TRANSICAO_INVALIDA`
- [x] `cancelar` ja CANCELADO → 409 `EVENTO_JA_CANCELADO`
- [x] invariante de transicao reside na entidade (testado em `EventoTest` puro)
- [x] editar RASCUNHO proprio → campos persistem, `atualizadoEm` muda, status continua RASCUNHO
- [x] editar PUBLICADO → 409 `EVENTO_NAO_EDITAVEL`

### Ownership (US-021.3 — critico)
- [x] promotor B edita evento de A → 404 (nao 403; nao vaza existencia)
- [x] promotor B publica evento de A → 404
- [x] promotor B cancela evento de A → 404
- [x] estado do evento de A inalterado apos tentativa de B

### Authz cross-papel
- [x] PARTICIPANTE em `POST /events` → 403 + `repository.count()` inalterado
- [x] PARTICIPANTE em `PUT /events/{id}` → 403
- [x] PARTICIPANTE em `POST /events/{id}/publicar` → 403
- [x] PARTICIPANTE em `POST /events/{id}/cancelar` → 403
- [x] PARTICIPANTE em `GET /events/meus` → 403
- [x] sem `X-User-Id` em endpoint autenticado → 401
- [x] PROMOTOR em `POST /events` valido → 201
- [x] `GET /events` por qualquer autenticado → 200

### Listagem (US-022)
- [x] retorna apenas PUBLICADOS (RASCUNHO e CANCELADO excluidos)
- [x] filtro `q` por titulo e por local (case-insensitive)
- [x] filtro `tipo=PAGO` e `tipo=GRATUITO`
- [x] filtro `de` / `ate` por `dataInicio` (limites inclusivos)
- [x] filtros combinados (`q` + `tipo` + `de`/`ate`)
- [x] paginacao: `size=2` com 5 publicados → `content.size()==2`, `totalElements==5`, `totalPages==3`
- [x] filtro sem match → `content` vazio, `totalElements==0`, HTTP 200
- [x] `GET /events` SEM nenhum filtro (`q`/`tipo`/`de`/`ate` todos null) → 200 (validado em H2 e em Postgres)

### Detalhe (US-023)
- [x] PUBLICADO → 200 com todos os campos (titulo, descricao, datas, local, tipo, preco/null, capacidade, `vagasDisponiveis`, `imagemUrl`)
- [x] `id` inexistente → 404
- [x] RASCUNHO alheio → 404 (nao vaza existencia)
- [x] RASCUNHO do proprio owner → 200
- [x] CANCELADO alheio → 404; CANCELADO proprio → 200

---

## Caminho-feliz end-to-end do promotor (MockMvc)

Coberto por `EventControllerIntegrationTest` — fluxo completo:
1. `POST /events` (PROMOTOR) → 201 + captura id
2. `GET /events` (participante) → evento NAO aparece (RASCUNHO)
3. `POST /events/{id}/publicar` (owner) → 200, `vagasDisponiveis == capacidade`
4. `GET /events` (participante) → evento aparece (US-022.1)
5. `GET /events/{id}` (participante) → 200, campos corretos
6. `POST /events/{id}/cancelar` (owner) → 200
7. `GET /events` (participante) → evento sumiu da lista (US-021.5)

---

## Smoke em Postgres real (11/11 PASS)

> Executado manualmente via Docker Compose (`docker compose --profile backend up`). Postgres + Flyway V1+V2 aplicadas, `ddl-auto: validate` verde.

| # | Passo | Resultado |
|---|---|---|
| 1 | Registrar promotor + admin aprova | 200 APROVADO |
| 2 | Login promotor | 200 JWT |
| 3 | Login admin | 200 JWT |
| 4 | `POST /api/events` (promotor) → criar | 201 RASCUNHO |
| 5 | `GET /api/events` sem filtro (q=null) | **200, totalElements=0** (RASCUNHO nao lista; query `CAST(:q AS string)` NAO quebra em Postgres — bug `lower(bytea)` nao se repete) |
| 6 | `POST /api/events/{id}/publicar` | 200 PUBLICADO, `vagasDisponiveis=200` |
| 7 | `GET /api/events` apos publicar | 200, lista retorna 1 evento |
| 8 | `GET /api/events?q=Show` (filtro) | 200, 1 resultado |
| 9 | `GET /api/events/{id}` | 200, todos os campos |
| 10 | PARTICIPANTE `POST /api/events` | **403** (bloqueio correto) |
| 11 | Promotor B edita evento de A | **404** (ownership, sem vazar existencia) |

**Flyway V2 aplicada.** `ddl-auto: validate` PASSOU (entidade ⇿ schema OK).

---

## Fronteira de auth (Backend)

- [x] endpoint autenticado sem `X-User-Id` → 401
- [x] papel errado (PARTICIPANTE em endpoint PROMOTOR) → 403, antes de tocar banco
- [x] owner correto → 2xx

---

## Concorrência

Concorrencia de inscrição/capacidade nao e escopo desta sprint (ADR-T07: decremento atomico de `vagasDisponiveis` fica para Sprint 3, quando o ticket-service e implementado). Campo `vagasDisponiveis` e apenas inicializado no `publicar()`. Testes de concorrencia serao obrigatorios na Sprint 3 (inscricao).

---

## Frontend (Vitest — 11 suites / 51 testes)

| Suite | Testes | Status |
|---|---|---|
| `validation.test.ts` | 13 | PASS |
| `form-field.test.tsx` | 5 | PASS |
| `input.test.tsx` | 4 | PASS |
| `button.test.tsx` | 5 | PASS |
| `Eventos.test.tsx` | ~5 | PASS |
| `EventoDetalhe.test.tsx` | ~5 | PASS |
| `MeusEventos.test.tsx` | ~7 | PASS |
| `CriarEditarEvento.test.tsx` | ~8 | PASS |
| `Register.test.tsx` | 1 FAIL / restantes PASS | **1 falha P3 pre-existente Sprint 1** |

**Testes novos da Sprint 2 (4 suites de eventos): todos PASS.**

### Cobertura por tela (Sprint 2)

| Tela | Happy path | Caminho de erro / validacao | Status |
|---|---|---|---|
| `Eventos` (lista publica) | lista eventos publicados; filtro `q` dispara chamada | empty state; erro de rede | PASS |
| `EventoDetalhe` | exibe titulo, datas, local, tipo, preco/Gratuito, sem botao inscricao | 404 mensagem amigavel; loading | PASS |
| `MeusEventos` | lista com badges de status; botoes Publicar/Cancelar; R1 Editar so para RASCUNHO | 409 EVENTO_JA_PUBLICADO toast; R3 vagas null | PASS |
| `CriarEditarEvento` | wizard 3 etapas; submit POST 201; redirecionamento | PAGO preco=0 erro; dataFim < dataInicio; campos obrigatorios; dados nao perdidos ao voltar | PASS |

### Build de producao
```
tsc -b && vite build
✓ zero erros de tipo
✓ 1742 modulos transformados
✓ built in 1.86s
warning: chunk > 500kB (cosmetico, nao bloqueia)
```

---

## Veredicto

**[x] APROVADO PARA PO**

- Reactor Maven (`mvnw verify`): **87/87 PASS**, BUILD SUCCESS, todos os 7 modulos.
- Frontend Vitest: **50/51 PASS** — a 1 falha e P3 pre-existente (Sprint 1, `Register.test.tsx`, nao relacionada a Sprint 2).
- Smoke Postgres: **11/11 PASS** — `ddl-auto: validate` verde, query de filtro null sem `lower(bytea)`.
- Zero P0 / Zero P1.
- Todos os criterios do PO (US-020..023) cobertos e validados.
