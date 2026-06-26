# Sprint 3 — Bugs encontrados na validação (Fase 6)

> Todos achados pelo **smoke de concorrência contra Postgres real** — nenhum apareceu no `mvnw verify` (H2/mocks). Confirma de novo a lição transversal: **validar em ambiente real é o gate que pega o que o CI esconde.** Todos P1, todos corrigidos; revalidado **14/14**.

## BUG-S3-01 — Saga falhava com 503 em Docker (wiring service-to-service ausente) · P1
- **Sintoma:** toda inscrição → **503 `EVENTO_INDISPONIVEL`**.
- **Causa:** a `ticket-service` no `docker-compose.yml` não recebia `EVENT_SERVICE_URL` → o default `http://localhost:8082` apontava pra **ela mesma** (dentro do container), não pro event-service. (Em prod/Railway daria o mesmo.)
- **Fix:** `908a03c` — `EVENT_SERVICE_URL=http://event-service:8082` + `INTERNAL_TOKEN` (ambos os serviços) + `depends_on event-service: service_healthy`.
- **Regressão:** coberto pelo smoke integrado (§2 do test-report); o caminho-feliz da inscrição só passa com o wiring correto.

## BUG-S3-02 — Validação do evento usava endpoint público que exige usuário · P1 (design cross-service)
- **Sintoma:** mesmo com wiring OK, inscrição → **503** (sem `ResourceAccessException` nos logs → não era rede).
- **Causa:** `EventClient.getEvento()` chamava `GET /events/{id}` (detalhe **público**, que exige `X-User-Id`). Numa chamada **service-to-service** só há `X-Internal-Token`, não há contexto de usuário → event-service respondia **401** → `EventClient` mapeava 4xx→`EVENTO_INDISPONIVEL` (503).
- **Fix:** `c812666` — novo **`GET /internal/events/{id}`** (autorizado por `X-Internal-Token`, retorna `EventoInternoResponse`); `EventClient` repontado pra ele. Mantém o saga idêntico; só a borda de leitura passou pro canal interno (consistente com ADR-T08: reads internos não passam por endpoints user-scoped).
- **Regressão:** `4776714` — 3 testes (`GET /internal/events/{id}`: 403 sem token / 200 com campos / 404 inexistente).

## BUG-S3-03 — Rota inexistente retornava 500 em vez de 404 · P1 (robustez, transversal)
- **Sintoma:** `POST /events/{id}/reservar-vaga` (rota que não existe) → **500 "Erro inesperado."**
- **Causa:** o `@ExceptionHandler(Exception.class)` (catch-all) dos `GlobalExceptionHandler` engolia o **`NoResourceFoundException`** do Spring 6.1 (lançado ao cair no resource handler de rota não-mapeada) e o transformava em 500. Mesma classe do aprendizado "erro de cliente nunca vira 500" (R-S2-02, type-mismatch).
- **Fix:** `71b4df2` — `@ExceptionHandler(NoResourceFoundException.class)` → **404** nos **3 serviços** (event, ticket, user — defeito compartilhado do template).
- **Regressão:** provado ponta a ponta no smoke (T3: a rota inexistente agora dá 404). *(Teste unitário de rota-404 via MockMvc não adicionado — comportamento do resource handler varia por ambiente; o e2e em servlet real é a prova autoritativa.)*

## Aprendizados a promover (para `rules/coding-standards.md` via `/validar-sprint`)
1. **Backend:** rota inexistente → **404** (handler de `NoResourceFoundException`), nunca 500. Generaliza R-S2-02.
2. **Arquitetura:** leitura/escrita **cross-service** usa o canal **interno** (`/internal/**` + `X-Internal-Token`), **nunca** endpoints públicos user-scoped (que exigem `X-User-*`).
3. **DevOps:** todo `depends_on`/URL **service-to-service** explícito no compose; o default `localhost` é uma armadilha em container. Subir o stack e exercer o **caminho integrado** (não só health) é parte do DoD.
