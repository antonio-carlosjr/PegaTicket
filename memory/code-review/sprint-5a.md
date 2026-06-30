# Code review acumulado — Sprint 5 · Trilha 5A (financeiro)

> Aprendizados que viraram regra em `rules/coding-standards.md` / dívida em `decisions.md`.

## Pego pelo CI (Testcontainers — não roda no Windows local)
- **TestcontainersBase singleton (recorrência S4):** `@Container static` numa base compartilhada por **2+ classes** → o extension para o container ao fim da 1ª classe → 2ª classe sem broker → listener em 30s timeout (ou HikariPool `total=0`). Fix: start manual em bloco `static` guardado por `isDockerAvailable()`, sem `@Container`. **Lição-mãe:** ao consertar um padrão de teste, aplicar a TODOS os serviços que o usam. → `coding-standards §Testes`.

## Revisor (opus) — 0 P0/P1; confirmou correto
- Refactor `EventService.cancelar(eventoId, promotorId)` (ordem trocada): todos os call sites conferidos, nenhum argumento invertido.
- Fiação AMQP do event-service (1ª mensageria): RabbitConfig delega ao autoconfigure; publish só em `afterCommit` (rollback não publica); fan-out com 2 filas (payment + `.ticket`) na mesma routing key → sem competing-consumers.

## Padrões confirmados como corretos (manter)
- Repasse = 1 UPDATE condicional em massa (`WHERE evento_id=? AND status='CONFIRMADO'`) — O(1), sem N+1.
- Reembolso = SELECT FOR UPDATE + loop (1 `reembolsos` por linha) — sem N+1 no SELECT.
- Corrida repasse-vs-reembolso: transições condicionais + row lock → exatamente 1 vencedor.
- Idempotência: `processed_events(event_id)` na mesma tx + ACK no-op (não lança → CR-S4-01).

## Follow-ups P2/P3 (→ owner / 5B)
- CR-5A-01 converter sem JavaTimeModule explícito (padronizar event/payment/ticket).
- CR-5A-02 `@Profile("!test")` no listener/config novos do ticket (padronizar com payment).
- CR-5A-03 `cancelarPorEvento()`/`Ingresso.cancelar()` código morto em prod (5B deve usar).
- CR-5A-04 índice parcial de reembolso fora do try/catch.
