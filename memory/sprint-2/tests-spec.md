# Sprint 2 — Especificação de Testes (TDD)

> Escrita **antes** do código. Tester implementa os specs vermelhos; Backend faz passar.
> Stack: JUnit 5 + AssertJ + Spring Boot Test + MockMvc. Profile `test` (H2 `MODE=PostgreSQL`, Flyway off, RabbitMQ excluído — ver `application-test.yml`).
> Gabarito: `services/user-service/.../AdminIntegrationTest.java` (`@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional`, headers `X-User-*` no MockMvc).

---

## ⚠️ Nota crítica: H2 mascara bugs de Postgres

O profile `test` usa **H2** com `ddl-auto: create-drop` e **Flyway desabilitado**. Isso significa:
1. O `ddl-auto: validate` **não é exercido** no CI (H2 cria o schema do zero a partir das entidades — nunca compara contra V1/V2). **Os bugs de mapeamento da Sprint 1 passaram exatamente por aqui.**
2. A query com `CAST(:q AS string)` e filtro `null` **não reproduz** o `lower(bytea)` no H2.

**Mitigações obrigatórias (na fase de validação, não opcional):**
- **Smoke em Postgres real:** subir `event-service` via `docker compose --profile backend up` (Postgres + Flyway V1+V2) e rodar o caminho-feliz manualmente OU adicionar um teste com **Testcontainers Postgres** para: (a) boot com `ddl-auto: validate` verde; (b) `GET /events?` sem filtros (filtro null) retorna 200, não 500.
- Mínimo aceitável se Testcontainers não couber no tempo: **smoke manual documentado** em `devops-log.md` — `POST /events` → `publicar` → `GET /events` → `GET /events/{id}` contra Postgres, + `GET /events` sem `q`.

---

## Pirâmide

| Camada | Foco | Onde |
|---|---|---|
| **Unitário** (maioria) | regra de domínio do `Evento` (transições, coerência) e do `EventService` (ownership, papel) | `EventServiceTest` (Spring slice ou puro com mocks de repo) |
| **Integração** (MockMvc) | controller→service→repo (H2): status codes, authz por header, listagem/paginação | `EventControllerIntegrationTest` |
| **Smoke Postgres** (1–2) | `validate` + filtro null | Testcontainers ou manual (ver nota crítica) |

Cobertura mínima: **`EventService` ≥ 80%**; controller coberto pelos testes de integração; front: happy + 1 erro por tela.

---

## Backend — `EventService` (domínio + regras)

### `criar`
- ✅ cria evento GRATUITO válido → status `RASCUNHO`, `promotorId = X-User-Id`, `vagasDisponiveis == null`, `preco == null`.
- ✅ cria evento PAGO válido (preço>0, prazo≥0) → RASCUNHO.
- ✅ rejeita PAGO com `preco == 0`/`null` → 400 (Bean Validation) **ou** 422 (se via service) — barrado na borda (US-020.5).
- ✅ rejeita GRATUITO com `preco != null` → 400 (coerência `chk_preco_pago`).
- ✅ rejeita `dataFim < dataInicio` → 400 (`@AssertTrue isPeriodoValido`).
- ✅ rejeita título/data/capacidade ausentes → 400 com mensagem inline por campo (US-020.4).

### Transições de estado (`publicar` / `cancelar`)
- ✅ `publicar` em RASCUNHO → status PUBLICADO **e** `vagasDisponiveis == capacidade` (US-021.2, criatério de sucesso §5).
- ✅ `cancelar` em RASCUNHO → CANCELADO.
- ✅ `cancelar` em PUBLICADO → CANCELADO (some da lista — verificado no teste de listagem).
- ✅ `publicar` em evento **já PUBLICADO** → **409** `EVENTO_JA_PUBLICADO` (não silencioso — risco do PO).
- ✅ `publicar` em evento **CANCELADO** → **409** `TRANSICAO_INVALIDA` (US-021.4).
- ✅ `cancelar` em evento **já CANCELADO** → **409** `EVENTO_JA_CANCELADO`.
- ✅ a invariante de transição vive na **entidade** (`Evento.publicar()` lança mesmo chamado fora do service) — teste unitário puro da entidade.

### Edição (`editar`)
- ✅ edita RASCUNHO próprio → campos persistem, status continua RASCUNHO, `atualizadoEm` muda (US-021.1).
- ✅ editar evento **PUBLICADO** → **409** `EVENTO_NAO_EDITAVEL`.

### Ownership (US-021.3 — crítico)
- ✅ promotor B edita evento do promotor A → **404** (não 403; não vaza existência).
- ✅ promotor B publica evento do promotor A → **404**.
- ✅ promotor B cancela evento do promotor A → **404**.
- ✅ o evento do promotor A **não é modificado** após a tentativa de B (assert no estado pós-tentativa).

---

## Backend — Authz cross-papel (MockMvc, headers)

> Réplica do `participanteNaoPodeAcessarAdminEndpoints`: setar `X-User-Papel`/`X-User-Id` no request.

- ✅ `PARTICIPANTE` em `POST /events` → **403** e **nenhum** evento criado (assert `repository.count()` inalterado — US-020.3).
- ✅ `PARTICIPANTE` em `PUT /events/{id}` → **403**.
- ✅ `PARTICIPANTE` em `POST /events/{id}/publicar` → **403**.
- ✅ `PARTICIPANTE` em `POST /events/{id}/cancelar` → **403**.
- ✅ `PARTICIPANTE` em `GET /events/meus` → **403** (endpoint é de promotor).
- ✅ sem header `X-User-Id` em endpoint autenticado → **401**.
- ✅ `PROMOTOR` em `POST /events` válido → **201** (caminho permitido).
- ✅ `ADMIN`/`PARTICIPANTE` em `GET /events` (lista pública) → **200** (qualquer autenticado lê).

---

## Backend — **Caminho-feliz do promotor** (end-to-end, MockMvc) ⭐

> Lição da Sprint 1: os bugs moraram **fora** do caminho-403. Este teste exercita o fluxo real e é **obrigatório**.

- ✅ **fluxo completo:**
  1. `POST /events` (PROMOTOR, payload válido) → 201, captura `id`.
  2. `GET /events` (participante) → o evento **NÃO** aparece (ainda RASCUNHO).
  3. `POST /events/{id}/publicar` (owner) → 200, `vagasDisponiveis == capacidade`.
  4. `GET /events` (participante) → o evento **aparece** na lista (US-022.1, critério de sucesso §1).
  5. `GET /events/{id}` (participante) → 200, todos os campos do detalhe corretos.
  6. `POST /events/{id}/cancelar` (owner) → 200.
  7. `GET /events` (participante) → o evento **sumiu** da lista (US-021.5 / US-022.5).

---

## Backend — Listagem (`GET /events`)

- ✅ retorna **apenas PUBLICADOS** (cria 1 RASCUNHO + 1 PUBLICADO + 1 CANCELADO → só o PUBLICADO volta — US-022.1).
- ✅ filtro `q` casa por `titulo` **e** por `local` (case-insensitive).
- ✅ filtro `tipo=PAGO` retorna só pagos; `tipo=GRATUITO` só gratuitos.
- ✅ filtro `de`/`ate` por `dataInicio` (intervalo) — limites inclusivos.
- ✅ **filtros combinados** (`q` + `tipo` + `de`/`ate`) retornam interseção correta (US-022.2).
- ✅ paginação: `size=2` com 5 publicados → `content.size()==2`, `totalElements==5`, `totalPages==3`; navegar `page=1` traz os próximos (US-022.3).
- ✅ filtro sem match → `content` vazio, `totalElements==0` (200, não erro — estado vazio US-022.4).
- ✅ **`GET /events` SEM nenhum filtro** (`q`/`tipo`/`de`/`ate` todos null) → 200. **Em Postgres** este é o teste do `lower(bytea)` (no H2 só valida o contrato; o smoke Postgres valida a query real).

---

## Backend — Detalhe (`GET /events/{id}`)

- ✅ `PUBLICADO` → 200 com todos os campos (título, descrição, datas, local, tipo, preço/null, capacidade, `vagasDisponiveis`, imagemUrl).
- ✅ `id` inexistente → **404**.
- ✅ **RASCUNHO de outro promotor** → **404** (não vaza existência — US-023.2).
- ✅ RASCUNHO **do próprio** owner → 200 (owner vê o próprio rascunho).
- ✅ CANCELADO alheio → 404; CANCELADO próprio → 200.

---

## Backend — Timezone (smoke, recomendado em Postgres)

- ✅ criar evento com `dataInicio = 2026-08-01T14:00:00-03:00` → ler o detalhe → o instante recuperado corresponde a 14:00 BRT (17:00Z). Garante `hibernate.jdbc.time_zone: UTC` + `TIMESTAMPTZ` round-trip sem "andar" o horário (risco de timezone do PO). **Falha silenciosa típica no H2** — preferir Postgres.

---

## Frontend (Vitest + Testing Library)

### Wizard criar evento (promotor)
- ✅ submete `POST` com payload montado (datas em ISO offset); toast de sucesso; redireciona para "Meus eventos".
- ✅ campos obrigatórios ausentes → erro inline; **não perde** dados já preenchidos ao voltar etapa (US-020.4).
- ✅ tipo PAGO com `preco=0` → erro de validação no front (espelha back — US-020.5).

### Meus eventos (promotor)
- ✅ lista eventos com badge de status; botões Publicar/Cancelar conforme status.
- ✅ clicar Publicar → chama `POST /{id}/publicar`; otimismo/refresh mostra PUBLICADO.

### Lista pública (participante)
- ✅ renderiza só publicados; busca `q` + filtro `tipo`/data dispara nova chamada.
- ✅ estado **empty** ("nenhum evento encontrado") quando lista vazia (US-022.4).
- ✅ estado **error** (mensagem clara) em falha de rede.

### Detalhe (participante)
- ✅ mostra data/hora **no fuso do usuário**, local, "Gratuito" ou preço, capacidade, imagem (se URL).
- ✅ **sem** botão de inscrição (US-023.4).
- ✅ estado **loading** enquanto carrega; **404** → mensagem amigável (US-023.3).

---

## Cobertura mínima
- `EventService` (transições/ownership/criação): **≥ 80%**.
- Controllers: cobertos pelos testes de integração MockMvc (todos os status codes acima).
- Front: por tela → happy path + 1 caminho de erro/validação.

## Definition of Done de testes
- [ ] Todos os ✅ acima implementados e verdes.
- [ ] Caminho-feliz end-to-end do promotor passando.
- [ ] Authz cross-papel (403) + ownership (404) cobertos.
- [ ] Listagem com filtro null validada **em Postgres** (Testcontainers ou smoke manual documentado).
- [ ] `ddl-auto: validate` verde em Postgres (smoke/Testcontainers) — não só H2.
- [ ] `./mvnw -B -ntp -pl services/event-service -am test` verde; `verify` reactor verde.
