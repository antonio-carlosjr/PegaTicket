# Sprint 4 — Regressão final (validar-sprint)

> Após os fixes do code review (CR-S4-01..04). CI local = verdade do GitHub Actions, exceto Testcontainers (Docker/JVM indisponível no Windows → roda no CI).

## Reactor (`./mvnw -B -ntp verify`) — BUILD SUCCESS
| Módulo | Run | Falhas | Skip (Testcontainers) |
|---|---|---|---|
| common-lib / api-gateway / user-service | — | 0 | — | (regressão das sprints anteriores verde) |
| event-service | inclui InternalEventPrecoTest | 0 | — |
| ticket-service | 49 | 0 | 13 |
| payment-service | 29 | 0 | 14 |

Novos testes de regressão do review verdes: `PagamentoAprovadoListenerGuardTest` (CR-S4-01), `GlobalExceptionHandlerWebTest` (CR-S4-03), `TicketControllerIntegrationTest#meusIngressos_incluiPendentePagamentoSemIngresso` (CR-S4-04).

## Frontend — `vite build` OK · `npm run test:run` 74/74
`tsc -b` limpo (fix do import morto, `4dec0d9`).

## Concorrência / idempotência (Testcontainers PG + RabbitMQ)
Pulam local (`disabledWithoutDocker=true`). **Confirmação no CI** (GitHub Actions ubuntu) na execução do PR. Cobrem A2 (última vaga PAGO), A3.a/b/c/d (listener + idempotência + guard), A4 (TTL), B1/B2 (escrow + confirmar), B5 (auth admin).

## Regressão funcional
- Fluxo **GRATUITO** da S3: `InscricaoServiceTest` (11) + `InscricaoConcorrenciaTest` verdes.
- Auth/erros tipados mantidos (handlers de borda reforçados no payment).

## Veredicto
**Verde local; PRONTO PARA PR.** Invariantes de concorrência/idempotência a confirmar pelo CI do PR.
