# Aprendizados de Code Review — Sprint 3 (Inscrição & Ingresso QR)

> Recorrências que viram **regra** em [`rules/coding-standards.md`](../../rules/coding-standards.md). Promovidas no `/validar-sprint 3`.

## R-S3-01 (Backend) — rota inexistente → 404, nunca 500
`@ExceptionHandler(NoResourceFoundException.class)` → 404. Spring 6.1 cai no resource handler ao não casar rota; sem o handler, o catch-all `Exception` vira 500. Generaliza R-S2-02 ("input do cliente nunca vira 500"). *(Origem: BUG-S3-03, smoke de roteamento. Aplicado nos 3 serviços.)*

## R-S3-02 (Arquitetura) — cross-service só pelo canal interno
Leitura/escrita entre serviços usa `/internal/**` + `X-Internal-Token`, **nunca** endpoint público user-scoped (exige `X-User-*`, que não existe numa chamada service-to-service). O endpoint interno não lê `X-User-*`; o gateway não roteia `/api/internal/**`. *(Origem: BUG-S3-02 — `getEvento` chamava `GET /events/{id}` público → 401→503. Coerente com ADR-T08.)*

## R-S3-03 (DevOps) — wiring service-to-service explícito + validar o caminho integrado
Toda URL/`depends_on` entre serviços no compose/env; o default `localhost` aponta pro próprio container. `mvnw verify`/H2 **não** pega config de runtime — subir o stack e exercer o fluxo integrado (não só `/health`) é DoD. *(Origem: BUG-S3-01 — `ticket-service` sem `EVENT_SERVICE_URL` → saga 503 em Docker; pego pelo smoke em Postgres real.)*

## R-S3-04 (Segurança) — comparação de segredo constante-no-tempo
`MessageDigest.isEqual(...)` (null-safe), nunca `String.equals` (curto-circuita → timing attack). Recorrência provável quando a Sprint 4 (pago) adicionar mais segredos inter-serviço. *(Origem: CR-S3-05.)*

## R-S3-05 (Backend) — hot path não relê o banco se o corpo é descartado
Se o consumidor faz `toBodilessEntity`, não pague `SELECT` extra na linha mais contendida — `UPDATE ... RETURNING` ou devolver sem reconsultar. *(Origem: CR-S3-02/03, P2 — diferido ao backlog para fix com `RETURNING`.)*

## Diferido (backlog, não vira regra agora)
- `EventService.reservarVaga/liberarVaga`: trocar `findById` do hot path por `UPDATE ... RETURNING` (CR-S3-02/03).
- `MeusIngressos`: dedupe por `eventoId` antes do fan-out + endpoint batch no event-service quando houver 3ª ocorrência (CR-S3-07).
- `EventoDetalhe`: refetch/`-1` de `vagasDisponiveis` após 201 (CR-S3-08); retry sem `window.location.reload()` (CR-S3-10).
