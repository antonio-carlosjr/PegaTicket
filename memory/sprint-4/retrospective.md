# Sprint 4 — Retrospectiva

**Tema:** Pagamento + escrow + saga de inscrição paga (US-040, US-041, US-060). Branch `feat/sprint-4-pagamento-escrow`. PR [#19](https://github.com/antonio-carlosjr/PegaTicket/pull/19).

## O que entregou
- payment-service de stub → serviço real (pagamento simulado, escrow retido, consumidores idempotentes).
- Saga assíncrona ponta-a-ponta: inscrição PAGO → `pedido.criado` → escrow → `confirmar` → `pagamento.aprovado` → ingresso. Primeira vez que o RabbitMQ saiu do papel.
- Frontend de checkout com feedback assíncrono (polling + timeout). Fluxo GRATUITO da S3 intacto.

## O que foi bem
- Spec detalhada (api-contracts + data-model com assinaturas exatas) manteve os 3 agentes paralelos (payment ‖ ticket ‖ front) **alinhados sem drift** — compilou de primeira no reactor.
- TDD vermelho → verde funcionou; idempotência/concorrência viraram testes desde o início.
- Revisor (opus) achou **2 bugs reais que os testes não pegavam**: poison-message (P0) e UI morta por INNER JOIN (P1). Code review profundo provou seu valor.

## O que doeu
- **Testcontainers não roda no Windows local** (Docker/JVM 400) → os invariantes da saga (concorrência/idempotência) só validam no **CI**. Custo: confiança adiada para o PR. Mitigação aceita (padrão desde a S3).
- Reincidência **CR-S3-03 → CR-S4-03** (500 em input inválido): cada serviço recria handlers de borda. → promovido: **base `@RestControllerAdvice` no common-lib** (dívida TECH).
- `pagamentos` não persiste `evento_id`/`promotor_id` → `pagamento.aprovado.eventoId` sai null; vira requisito da S5 (repasse). TECH-S4-01.

## Ações / dívidas (para S5)
- **TECH-S4-01** persistir `evento_id`/`promotor_id` em `pagamentos` (migration payment V3) — pré-requisito do repasse.
- **TECH-S4-02** tela admin de escrow (endpoint pronto; falta UI) — alta prioridade.
- **TECH-S4-03** prazo restante no card pendente (precisa `inscritoEm` no response).
- **Base advice no common-lib** (handlers de borda) — encerra a reincidência.

## Regras promovidas (`coding-standards.md`)
- Mensageria: idempotência cobre o "evento tarde demais" (ACK no-op, sem poison message). *(CR-S4-01)*
- Job/método com I/O remoto não segura tx; cuidado com auto-invocação de proxy AOP. *(CR-S4-02)*
- Handlers de borda centralizados num advice base no common-lib. *(CR-S4-03)*

## Métricas
- 17 commits (`377b86d → docs(step5)`), todos atômicos, sem `Co-Authored-By`.
- Reactor `mvn verify` BUILD SUCCESS; frontend 74/74 + build; 0 P0/P1 no review.
