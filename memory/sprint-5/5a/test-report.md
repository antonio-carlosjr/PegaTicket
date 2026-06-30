# Sprint 5 · Trilha 5A — Relatório de Testes (Fase 6)

> Repasse (US-043) + reembolso por evento cancelado (US-042). Testcontainers não rodam no Windows local (Docker inacessível pela JVM) → integração valida no **CI** (padrão S4).

## Suíte executável LOCAL (sem Docker) — VERDE
`./mvnw -B -ntp verify` → **BUILD SUCCESS** (reactor inteiro, 0 falhas). `npm run test:run` → **80/80** + `tsc` limpo.

| Módulo | Unit verdes | Skipped (Testcontainers) | Regressão |
|---|---|---|---|
| event-service | `EventoRealizarTest` 4/4, `EventoPublisherAfterCommitTest` 3/3, `EncerrarEventoControllerTest` 6/6 (76 total) | A3.a/A4 (publicação), VagaConcorrencia | S2/S3 verdes |
| payment-service | `PagamentoRepassarReembolsarTest` 6/6 (47 total) | B1, B2, C1, C2 | saga S4 intacta |
| ticket-service | `InscricaoCancelamentoPorEventoTest` 7/7 (61 total) | D1, D2 | saga S4 intacta |
| frontend | 80/80 (Encerrar + extrato) | — | — |

## Suíte de integração (Testcontainers PG+Rabbit) — PENDENTE DE CI
Cobre os invariantes da saga financeira — só conta como "passou" no CI com Docker:
- **A3/A4** event publica `evento.finalizado`/`evento.cancelado` em afterCommit; rollback não publica.
- **B2** repasse pós-finalizado + idempotência de reentrega (1× por pagamento).
- **C1** reembolso em massa (N CONFIRMADO→REEMBOLSADO + N `reembolsos`) + idempotência.
- **C2** corrida repasse-vs-reembolso → exatamente um vence.
- **D1/D2** ticket cancela inscrições/ingressos por `evento.cancelado.ticket`; preserva UTILIZADO; idempotente.

## Pontos de risco para o CI (1ª mensageria do event-service)
- event-service ganhou RabbitMQ pela 1ª vez — config/perfil de teste são exatamente onde os bugs do S4 se esconderam. O agente já: removeu a exclusão de `RabbitAutoConfiguration` do `application-test-postgres.yml`, usou `TestcontainersBase` singleton, purga de filas no `@BeforeEach`, RabbitConfig delegando ao autoconfigure.
- **Fan-out** `evento.cancelado` (payment) + `evento.cancelado.ticket` (ticket) — 2 filas, 1 routing key. Validação só no CI.

## Loop de bug
- **P0/P1 na suíte local: 0.** Build verde, regressão intacta.
- Integração: não executável aqui → ver `bugs.md`. Esperado (como no S4) que o CI possa revelar ajustes de fiação AMQP/perfil; serão corrigidos no `/validar-sprint 5a`.
