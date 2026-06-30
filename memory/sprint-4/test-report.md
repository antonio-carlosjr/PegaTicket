# Sprint 4 — Relatório de Testes (Fase 6)

> Saga de inscrição paga (US-040/041/060). Tester + orquestrador.
> **Ambiente local:** Docker Desktop responde **400** ao `/info` pela JVM (Testcontainers) em todos os pipes — limitação conhecida do Windows (a CLI `docker` funciona, a JVM não alcança o daemon). Por isso os testes de integração **pulam local** (`@Testcontainers(disabledWithoutDocker=true)`) e são validados no **CI (GitHub Actions / ubuntu)**.

## Suíte executável LOCAL (sem Docker) — VERDE
| Módulo | Testes | Falhas | Pulados | Observação |
|---|---|---|---|---|
| common-lib | (build) | 0 | — | SUCCESS |
| event-service | inclui `InternalEventPrecoTest` (3) | 0 | — | preco/promotorId no resumo interno |
| ticket-service | 44 run | 0 | 12 | regressão GRATUITO (S3) verde; ramo PAGO unit verde |
| payment-service | 27 run (reactor) | 0 | 14 | escrow HALF_UP, `confirmar()` idempotente, afterCommit |
| **frontend (Vitest)** | **74** | **0** | — | checkout PAGO, polling+timeout 60s, ingresso pendente; `tsc --noEmit` limpo |

`./mvnw -B -ntp -pl event,ticket,payment -am test` → **BUILD SUCCESS** (0 falhas). `npm run test:run` → **74/74**.

## Suíte de integração (Testcontainers PG + RabbitMQ) — PENDENTE DE CI
Pulada local (Docker 400). Cobre os invariantes críticos da saga — **só conta como "passou" quando rodar com Docker no CI** (ressalva #3 do PO):

| Caso | História | O que valida |
|---|---|---|
| A2 — concorrência última vaga PAGO (`@RepeatedTest(3)`) | US-040/041 | 1 reserva, K−1×409, 0 pagamento dos perdedores (Postgres real) |
| A3.b — `pagamento.aprovado` reentregue | US-060/041 | **1 ingresso só** (idempotência) |
| A3.a/c — listener emite ingresso + ATIVA / inexistente→DLQ | US-041 | emissão correta |
| A4 — `ExpiracaoReservaJob` | US-040 (TTL) | pendente vencida → EXPIRADA + libera vaga |
| B1.a/b — consumir `pedido.criado` (+ reentrega) | US-040/060 | 1 pagamento PENDENTE com escrow; reentrega → 1 |
| B2.a-f — confirmar (escrow, publica, idempotente, 403/404/402) | US-040 | transição CONFIRMADO, publica `pagamento.aprovado` |
| B4/A5 — `afterCommit` não publica em rollback | US-060 | sem evento fantasma |
| B5 — auth do `GET /payments` (admin) | US-040.3 | 401/403 por papel |

## Loop de bug
- **P0/P1 na suíte executável local: 0.** Nenhuma falha em unit/regressão.
- Itens de integração: não executáveis aqui → ver `bugs.md` (status: aguardando CI).

## Recomendação
Rodar **`/validar-sprint 4`** (Revisor + CI com Docker) para executar a suíte Testcontainers e confirmar os invariantes da saga antes do merge. Opcional: smoke local via `docker compose` (padrão S3) para validar a fiação RabbitMQ/env ponta-a-ponta.
