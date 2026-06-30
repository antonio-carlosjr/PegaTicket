# Sprint 5A — Backend Log (payment-service)

> Agente: Backend (sonnet). Data: 2026-06-30. Fase 5 da Trilha 5A.

## Escopo implementado

- TECH-S4-01 fechado: `evento_id`/`promotor_id` persistidos em `pagamentos`.
- US-043: repasse pós-evento (`EventoFinalizadoListener`).
- US-042: reembolso em massa por cancelamento (`EventoCanceladoListener`).
- RA2: `PagamentoResponse` expõe `eventoId`/`promotorId`.

## Arquivos criados / alterados

| Arquivo | Ação |
|---|---|
| `db/migration/V3__repasse_reembolso.sql` | CRIADO — colunas + índice + UNIQUE parcial reembolso |
| `domain/Pagamento.java` | ALTERADO — eventoId/promotorId + repassar()/reembolsar() + overload factory |
| `domain/Reembolso.java` | ALTERADO — factory `criar(...)` |
| `repository/PagamentoRepository.java` | ALTERADO — `repassarConfirmadosDoEvento` + `findConfirmadosDoEventoForUpdate` |
| `repository/ReembolsoRepository.java` | CRIADO — JpaRepository simples |
| `messaging/EventoFinalizadoEvent.java` | CRIADO — record AMQP |
| `messaging/EventoCanceladoEvent.java` | CRIADO — record AMQP |
| `messaging/EventoFinalizadoListener.java` | CRIADO — listener repasse idempotente |
| `messaging/EventoCanceladoListener.java` | CRIADO — listener reembolso idempotente |
| `messaging/RabbitConfig.java` | ALTERADO — filas/bindings `evento.cancelado` + DLQ |
| `service/PagamentoService.java` | ALTERADO — `criarPendente` passa eventoId/promotorId; confirmar usa `salvo.getEventoId()` |
| `dto/PagamentoResponse.java` | ALTERADO — campos `eventoId`/`promotorId` (additive, não-breaking) |

## Decisões de concorrência (coding-standards §4)

| Cenário | Estratégia escolhida | Justificativa |
|---|---|---|
| Repasse em massa | `UPDATE ... WHERE evento_id=? AND status='CONFIRMADO'` (1 round-trip) | O(1) — não há loop N+1; row lock do Postgres serializa vs. reembolso |
| Reembolso em massa | `SELECT ... FOR UPDATE` + loop inserindo 1 reembolso por pagamento | Necessário 1 INSERT por linha; SELECT FOR UPDATE carrega todos com lock de linha na mesma tx |
| Corrida repasse-vs-reembolso | Transições condicionais `WHERE status='CONFIRMADO'` em ambas as sagas | Apenas uma vence; a outra encontra 0 linhas e faz ACK no-op (never poison message — CR-S4-01) |
| Reentrega at-least-once | `processed_events(event_id UUID PK)` — INSERT; PK colide na reentrega → `DataIntegrityViolationException` → return (ACK no-op) | Padrão S4 reusado; tx desfaz automaticamente o INSERT antes do return |

## Compatibilidade retroativa

- `Pagamento.pendente(4 params)` mantido como overload legado delegando ao novo (6 params) com `null`s para eventoId/promotorId. Testes `PagamentoConfirmarIdempotenciaTest` e `EscrowCalculoTest` não foram alterados — continuam verdes.
- `PagamentoResponse` ganhou 2 campos no final do record — non-breaking (additive).

## Resultado dos testes locais

- `test-compile`: BUILD SUCCESS (25 src + 14 test-classes).
- Unitários (sem Docker): 20 testes, 0 falhas, 0 erros.
  - `EscrowCalculoTest`: 6/6 ✅
  - `PagamentoConfirmarIdempotenciaTest`: 4/4 ✅
  - `PagamentoRepassarReembolsarTest`: 6/6 ✅ (era vermelho antes desta fase)
  - `GlobalExceptionHandlerWebTest`: 2/2 ✅
  - `AfterCommitRollbackTest`: 2/2 ✅
- `PaymentServiceApplicationTests` (context loads com H2): 1/1 ✅
- Testes Testcontainers (B1/B2/C1/C2): pendentes CI (Docker não disponível localmente — padrão `@Testcontainers(disabledWithoutDocker=true)`).

## Pendências para CI

Os seguintes testes precisam de Docker (Postgres + RabbitMQ reais) e rodam apenas no CI:
- `PedidoCriadoEventoIdIntegrationTest` (B1.a, B1.b)
- `EventoFinalizadoListenerIntegrationTest` (B2.a..e)
- `EventoCanceladoListenerIntegrationTest` (C1.a..d)
- `CorridaRepasseReembolsoTest` (C2, @RepeatedTest(3))
- `PedidoCriadoListenerIntegrationTest` (existente)
- `PagamentoServiceIntegrationTest` (existente)
- `PaymentControllerIntegrationTest` (existente)

## Riscos / divergências

- Nenhuma divergência em relação ao `data-model.md §A` ou `api-contracts.md §3`.
- A saga do S4 (pedido.criado → pagamento.aprovado → inscricao confirmada) permanece intacta — `PedidoCriadoListener` e `PagamentoAprovadoPublisher` não foram alterados.
- UNIQUE parcial `uk_reembolso_evento_cancelado` ativado (PO aprovou — RA1): barra reembolso duplicado mesmo com `eventId` diferente, defesa extra além do `processed_events`.
