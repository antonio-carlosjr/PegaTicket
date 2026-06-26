# Sprint 3 — Retrospectiva (Inscrição & Ingresso QR)

## O que deu certo
- **Concorrência acertada de primeira no design:** decremento atômico + `UNIQUE` + saga com compensação foram especificados na arquitetura (ADR-T07) e implementados sem race — o **smoke de concorrência em Postgres real passou 14/14** (20 paralelas na última vaga → 5/5 sem overbooking; dupla inscrição concorrente → 1/1).
- **Validar em Postgres real continua sendo o gate que importa:** os **3 bugs P1** (saga 503 por wiring; validação via endpoint público; rota→500) **só** apareceram no stack real — `mvnw verify`/H2/mocks passavam verdes. Reforço definitivo da lição transversal das Sprints 1–2.
- **Defesa em profundidade do canal interno (ADR-T08)** validada por teste e por smoke: `/internal/**` fora do gateway, `X-Internal-Token` 403, anti-spoof.
- **Revisor (opus) sem P0/P1** e com 14 confirmações de correção — deu confiança ao gate em vez de só achar problemas; os P2 viraram melhorias reais (token timing-safe) ou backlog priorizado.

## Desafios / o que pegou
- **Wiring service-to-service:** o `EVENT_SERVICE_URL` faltando no compose fez a saga falhar 503 — invisível no CI (H2/mocks), só no Postgres real. Virou regra (R-S3-03).
- **Endpoint público em chamada interna:** `getEvento` batia no detalhe user-scoped → 401→503. A leitura interna precisou de um `/internal/events/{id}` próprio. Virou regra (R-S3-02).
- **Testcontainers pula local (Windows):** o Maven-JVM não alcança o daemon; os testes de concorrência só rodam no CI Linux. Mitigado com o smoke manual integrado — mas é um gap de DX local a observar.

## Regras promovidas (ver `memory/code-review/sprint-3.md` → `coding-standards.md`)
1. Rota inexistente → **404** (`NoResourceFoundException`), nunca 500.
2. Cross-service só pelo **canal interno** (`/internal/**` + `X-Internal-Token`).
3. **Wiring service-to-service explícito** no compose + validar o caminho integrado.
4. Segredo compartilhado: comparação **constante-no-tempo**.
5. Hot path não relê o banco se o corpo é descartado.

## Dívida assumida (backlog)
- `findById` extra no hot path de `reservar/liberar-vaga` → fix com `UPDATE ... RETURNING` (CR-S3-02/03).
- Fan-out N+1 no front (`MeusIngressos`) → dedupe + endpoint batch quando crescer (CR-S3-07).
- `vagasDisponiveis` stale na tela de detalhe pós-inscrição (CR-S3-08); retry com reload (CR-S3-10).
- `INTERNAL_TOKEN` em prod (Railway) deve sobrescrever o default `dev-internal-secret`.

## Para a Sprint 4 (Pagamento — escrow/saga AMQP)
- É a sprint de **saga assíncrona com dinheiro** — produtor publica **após commit**, consumidor **idempotente** (`processed_events`), DLQ. Mais segredos inter-serviço → a regra do timing-safe (R-S3-04) já antecipa.
- Reaproveitar o padrão de compensação da mini-saga síncrona desta sprint.
