# Sprint 5 · Trilha 5B (experiência) — Relatório de Testes (Fase 6)

> US-034 (check-in QR) + US-035 (cancelar inscrição + reembolso individual) + US-024/025 (avaliação + reputação). Testcontainers não rodam no Windows local (Docker inacessível pela JVM) → integração valida no **CI** (padrão S4/5A).

## Suíte executável LOCAL (sem Docker) — VERDE
`./mvnw -B -ntp verify` → **BUILD SUCCESS** (reactor inteiro, 7 módulos, 0 falhas). Frontend: `npm test -- --run` → **97/97** (22 arquivos) + `tsc` limpo.

| Módulo | Tests run | Verdes | Skipped (Testcontainers) | Falhas |
|---|---|---|---|---|
| event-service | 121 | 110 | 11 | 0 |
| ticket-service | 101 | 79 | 22 | 0 |
| payment-service | 53 | 21 | 32 | 0 |
| frontend | 97 | 97 | — | 0 |

Regressão S2/S3/S4/5A intacta em todos os módulos (saga financeira e fluxos anteriores verdes).

## Cobertura por história (unit/controller local — VERDE)
- **US-034** `IngressoRealizarCheckinTest` 4/4 (409 se UTILIZADO/CANCELADO), `CheckinControllerTest` 8/8 (POST /tickets/checkin, autorização promotor+dono via 403), frontend `CheckinScanner` 5/5.
- **US-035** `CancelamentoInscricaoServiceTest` 8/8, `CancelamentoControllerTest` (DELETE inscrição: 200 gratuito, 200 pago+reembolso, 422 fora do prazo, 403 de outro, 401 sem user, 400 id inválido), `InscricaoCancelamentoPorEventoTest` 7/7, frontend `CancelarInscricao`.
- **US-024/025** event: avaliação (nota 1-5, 1 por usuário/evento) + reputação (média/total); ticket `InternalTicketControllerTest` 8/8 (canal `participou` com token interno, 400 em param ausente/inválido, 403 sem/errado token), frontend `AvaliacaoEvento`.

## Suíte de integração (Testcontainers PG+Rabbit) — PENDENTE DE CI
Cobre os invariantes que só contam como "passou" no CI com Docker:
- **Check-in concorrente** (`CheckinConcorrenciaTest`): 2 check-ins simultâneos no mesmo ingresso → 1 vence (UNIQUE ingresso_id / row lock).
- **Cancelamento concorrente** (`CancelamentoConcorrenciaTest`): 2 cancelamentos → 1 vence, 2º → 409.
- **Reembolso individual** (`InscricaoCanceladaListenerIntegrationTest`, payment): consome `inscricao.cancelada` → 1 reembolso `CANCELAMENTO_PARTICIPANTE`; idempotente.
- **Reembolso individual vs massa** (`ReembolsoIndividualVsMassaConcorrenciaTest`): corrida entre cancelamento individual e cancelamento do evento → exatamente um reembolso.
- **afterCommit** `InscricaoCanceladaAfterCommitTest`: publica só após commit; rollback não publica.
- **Elegibilidade participou** (`InternalTicket`… reads sob Testcontainers): ingresso UTILIZADO / inscrição ATIVA / CANCELADA / sem vínculo.

## Pontos de risco para o CI
- **1ª mensageria outbound do event-service** (`TicketClient` HTTP síncrono para o canal interno do ticket) — fail-closed 503 se ticket indisponível; validação end-to-end só no CI.
- **Fan-out de reembolso**: `inscricao.cancelada` (individual, novo) coexiste com `evento.cancelado` (massa, 5A) — a corrida individual-vs-massa é o ponto mais sensível.
- Publishers do ticket agora com `RabbitTemplate` opcional (no-op sob perfil `test`); o caminho real de publicação só é exercido no CI (test-postgres).

## Loop de bug
- **P0/P1 remanescentes na suíte local: 0.** Build verde, regressão intacta.
- 4 defeitos foram **caçados e corrigidos na própria Fase 5** (antes do commit da implementação) — ver `bugs.md`. Padrão S4/5A: o CI ainda pode revelar ajustes de fiação AMQP/perfil, que entram no loop do `/validar-sprint 5b`.
