# Code review acumulado — Sprint 4 (saga de pagamento)

> Aprendizados que viraram regra em `rules/coding-standards.md` / dívida em `decisions.md`.

## P0/P1 (corrigidos no `/validar-sprint 4`)
- **CR-S4-01 (P0) — Poison message por evento "tarde demais".** `pagamento.aprovado` chegando para inscrição `EXPIRADA` fazia `ativar()` lançar → rollback desfazia o `INSERT processed_events` → reentrega infinita → DLQ. **Regra:** consumidor idempotente trata estado inesperado com **ACK no-op**, nunca deixa lançar. → `coding-standards §1 Mensageria`.
- **CR-S4-02 (P1) — Job `@Transactional` segurando tx durante I/O.** `ExpiracaoReservaJob` chamava `liberarVaga()` (HTTP) dentro da transação. **Regra:** I/O remoto fora da tx; cuidado com auto-invocação de proxy AOP. → `coding-standards §1 Concorrência`.
- **CR-S4-03 (P1) — 500 em query inválida.** `GET /payments?status=foo` → `valueOf` lança → catch-all 500. **Reincidência de CR-S3-03.** **Regra:** centralizar handlers de borda num `@RestControllerAdvice` base no `common-lib`. → `coding-standards §1`.
- **CR-S4-04 (P1) — UI morta por INNER JOIN.** `/tickets/me` não trazia `PENDENTE_PAGAMENTO` (sem ingresso) → card "aguardando pagamento" nunca aparecia; teste front passava só por mock. **Lição:** consulta de listagem que deve incluir estado-sem-filho usa **LEFT JOIN**; teste de integração (não só mock) pega isso.

## Padrões confirmados como corretos (manter)
- `event_id` (UUID na origem) + `processed_events` na **mesma tx** do efeito + publish em `afterCommit` = exactly-once-effect (ADR-T11). Constraints `UNIQUE(inscricao_id)` como rede final.
- Reserva atômica de vaga (ADR-T07) reusada no ramo PAGO antes do pagamento.
- Token interno constante-no-tempo + `/internal/**` não roteado pelo gateway (ADR-T08).

## Follow-ups abertos (P2/P3 → backlog/owner)
- CR-S4-05: `confirmar` numa inscrição expirada deveria devolver 409 `INSCRICAO_EXPIRADA` (hoje 404).
- CR-S4-06: `eventoId` null em `pagamento.aprovado` (= TECH-S4-01: persistir `evento_id`/`promotor_id` em `pagamentos` na S5).
- CR-S4-07..09: simplificar injeção do publisher; uniformizar gates de consumidor; avaliar publisher-confirms.
- CR-S4-10/11 (Arquiteto): latência TTL+job; revisitar contrato do evento.
- Recorrência → **base `@RestControllerAdvice` no common-lib** (CR-S3-03 + CR-S4-03).
