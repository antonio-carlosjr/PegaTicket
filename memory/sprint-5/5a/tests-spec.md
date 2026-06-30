# Sprint 5 — Trilha 5A (Financeiro) — Especificacao de Testes (TDD)

> Testes **vermelhos primeiro**. Cada caso marcado com a historia (US-043/US-042/TECH-S4-01).
> Saga/idempotencia/corrida exigem **Testcontainers Postgres + RabbitMQ** (broker real — `@RabbitListener`/row lock nao sao exercitados por H2/mock).
> **APRENDIZADOS S4 (obrigatorios — custaram 6 ciclos de CI; Testcontainers nao roda no Windows local, so no CI):**
> - `TestcontainersBase` = **SINGLETON** (start manual em bloco `static` guardado por `DockerClientFactory.isDockerAvailable()`, **sem** `@Container`) — senao container para entre classes (HikariPool total=0).
> - `@BeforeEach` **purga as filas** (`rabbitAdmin.purgeQueue(nome, false)`) nos testes que compartilham contexto/broker.
> - **RabbitConfig delega ao autoconfigure**: NAO declarar `RabbitTemplate` proprio, NAO `@ConditionalOnBean(ConnectionFactory.class)`. So o `Jackson2JsonMessageConverter` (+`JavaTimeModule`) + filas/exchanges/bindings.
> - Perfil `application-test-postgres.yml` **NAO exclui** `RabbitAutoConfiguration` (listeners precisam do RabbitTemplate); Postgres-only excluem via `@DynamicPropertySource` e mockam o publisher.
> - **Dinheiro:** `BigDecimal.setScale(2)` no dominio + `@JsonFormat(shape=STRING)` nas respostas.
> - Consumidor trata "evento tarde demais" com **ACK no-op** (nao deixa transicao lancar → poison message). Publish em `afterCommit`.
> Cobertura: services de repasse/reembolso ≥90%; resto ≥70%.

---

## A. event-service — gatilho + publicacao  [Testcontainers PG+Rabbit, salvo onde marcado]

### A1. Evento.realizar() — maquina de estados  (US-043)  [unit, H2/puro]
- [US-043] `realizar_publicado_transicionaParaRealizado`: PUBLICADO → `realizar()` → `status=REALIZADO`, `realizadoEm != null`.
- [US-043] `realizar_rascunho_lanca409`: RASCUNHO → `TRANSICAO_INVALIDA` (409); status inalterado.
- [US-043] `realizar_cancelado_lanca409`: CANCELADO → `TRANSICAO_INVALIDA` (409).
- [US-043] `realizar_jaRealizado_lanca409`: REALIZADO → `EVENTO_JA_REALIZADO` (409) — guard de re-encerramento.

### A2. POST /events/{id}/encerrar — auth + ownership  (US-043)  [MockMvc/Integration]
- [US-043] `encerrar_promotorDono_retorna200_eStatusRealizado`: `X-User-Papel=PROMOTOR` + owner → 200, body `status=REALIZADO`.
- [US-043] `encerrar_semUserId_retorna401`.
- [US-043] `encerrar_papelParticipante_retorna403` (`X-User-Papel != PROMOTOR`).
- [US-043] `encerrar_naoDono_retorna404` (promotor diferente → nao vaza existencia, padrao `carregarComOwnership`).
- [US-043] `encerrar_eventoRascunho_retorna409` (`TRANSICAO_INVALIDA`).
- [US-043] `encerrar_idNaoNumerico_retorna400` (handler de borda, nunca 500).

### A3. Publicacao de evento.finalizado em afterCommit  (US-043)  [PG+Rabbit]
- [US-043] `encerrar_publicaEventoFinalizado_aposCommit`: apos `encerrar`, mensagem aparece na fila `evento.finalizado` com `eventId != null`, `eventoId` correto, `promotorId` correto. (consumir da fila com `rabbitTemplate.receiveAndConvert` ou listener de teste).
- [US-043] **`encerrar_afterCommit_naoPublicaEmRollback`** [unit, mock RabbitTemplate] **(CRITICO)**: forcar exceção na tx de `encerrar` apos `registerSynchronization` → rollback → `verifyNoInteractions(rabbitTemplate)`. Caminho feliz → `convertAndSend` 1x apos commit.

### A4. Publicacao de evento.cancelado em afterCommit  (US-042)  [PG+Rabbit]
- [US-042] `cancelar_publicaEventoCancelado_aposCommit`: apos `cancelar` (endpoint ja existente), mensagem na fila `evento.cancelado` (e copia em `evento.cancelado.ticket`) com `eventId`/`eventoId`/`promotorId`.
- [US-042] `cancelar_2x_publicaApenas1Vez`: 2o `cancelar` → 409 `EVENTO_JA_CANCELADO` → **nenhuma** 2a mensagem (guard de dominio).
- [US-042] `cancelar_afterCommit_naoPublicaEmRollback` [unit]: rollback → RabbitTemplate nunca chamado.

---

## B. payment-service — repasse  (US-043, TECH-S4-01)  [Testcontainers PG+Rabbit]

### B1. TECH-S4-01 — persistir evento_id/promotor_id  (TECH-S4-01)  [PG ou unit]
- [TECH-S4-01] `criarPendente_persisteEventoIdEPromotorId`: consumir `pedido.criado{eventoId=10, promotorId=7, valor=100}` → `Pagamento` salvo com `eventoId=10`, `promotorId=7`, `valorBruto=100.00`. (o payload ja carrega os ids; o teste prova que sao gravados.)
- [TECH-S4-01] `pagamentoResponse_incluiEventoIdEPromotorId`: `GET /payments/me` → JSON com `eventoId`/`promotorId` (null para pagamento legado sem os campos).

### B2. Repasse pos evento.finalizado  (US-043)  [PG+Rabbit]
- [US-043] `eventoFinalizado_repassaConfirmadosDoEvento`: 3 pagamentos `CONFIRMADO` do `eventoId=10` (+1 CONFIRMADO de outro evento, +1 PENDENTE do 10) → publica `evento.finalizado{eventoId=10}` → apos consumo: os **3** do evento 10 → `REPASSADO` (`repassadoEm != null`); o de outro evento e o PENDENTE **inalterados**; 1 linha em `processed_events`.
- [US-043] **`eventoFinalizado_reentregue2x_repassaApenas1Vez`** **(CRITICO)**: publica 2x a **mesma** mensagem (mesmo `eventId`) → cada pagamento fica `REPASSADO` 1x (sem re-transicao; `repassadoEm` da 1a entrega), 1 linha em `processed_events`. (idempotencia via PK.)
- [US-043] `eventoFinalizado_pagamentoNaoConfirmado_naoToca` (US-043 crit.4): pagamento ja `REEMBOLSADO` do evento → `evento.finalizado` **nao** o altera (clausula `status='CONFIRMADO'`). Permanece `REEMBOLSADO`.
- [US-043] `eventoFinalizado_semPagamentos_ackNoop`: evento sem pagamentos CONFIRMADO → consumidor faz no-op (0 linhas afetadas), ACK, `processed_events` gravado, sem erro/DLQ. (guard "evento tarde demais".)
- [US-043] `valorRepasse_jaComputadoNoS4_naoRecalcula`: `valorRepasse` = `valorBruto - valorTaxa` (90.00 p/ 100.00) permanece o do S4; repasse so muda `status`/`repassadoEm`.

---

## C. payment-service — reembolso em massa  (US-042)  [Testcontainers PG+Rabbit]

### C1. Reembolso pos evento.cancelado  (US-042)  [PG+Rabbit]
- [US-042] **`eventoCancelado_reembolsaConfirmadosDoEvento`** **(CRITICO)**: 3 pagamentos `CONFIRMADO` do `eventoId=10` → publica `evento.cancelado{eventoId=10}` → apos consumo: os 3 → `REEMBOLSADO` (`reembolsadoEm != null`); **3 registros** em `reembolsos` (`motivo='EVENTO_CANCELADO'`, `status='PROCESSADO'`, `valor=valor_bruto`); 1 linha em `processed_events`.
- [US-042] **`eventoCancelado_reentregue2x_reembolsaApenas1Vez`** **(CRITICO)**: 2x mesma mensagem (mesmo `eventId`) → 3 pagamentos `REEMBOLSADO`, **3 reembolsos** (nao 6), 1 `processed_events`. (idempotencia PK; na 2a entrega o SELECT `status='CONFIRMADO'` ja nao traz nada.)
- [US-042] `eventoCancelado_pagamentoPendente_naoReembolsa`: pagamento `PENDENTE` do evento → nao vira REEMBOLSADO, nenhum `reembolsos` criado (so CONFIRMADO e reembolsado).
- [US-042 crit.5] `admin_listaReembolsadosERepassados`: `GET /api/payments?status=REEMBOLSADO` (admin) lista os reembolsados; `?status=REPASSADO` lista os repassados (auditoria).

### C2. CORRIDA repasse-vs-reembolso no mesmo pagamento  (US-042/US-043, §7 concorrencia)  [PG+Rabbit] **(CRITICO)**
- [US-042/US-043] `corrida_repasseEReembolso_mesmoPagamento_apenasUmVence`: 1 pagamento `CONFIRMADO` do `eventoId=10`; disparar `evento.finalizado{10}` e `evento.cancelado{10}` (eventIds distintos) concorrentemente → o pagamento termina em **exatamente um** de {`REPASSADO`, `REEMBOLSADO`}, **nunca os dois**; se REEMBOLSADO venceu, **0** repasse (status nao casou); se REPASSADO venceu, **0** reembolso criado. (row lock + `WHERE status='CONFIRMADO'` serializam.) `@RepeatedTest(3)`.

---

## D. ticket-service — cancelamento em massa  (US-042)  [Testcontainers PG+Rabbit]

### D1. Consumir evento.cancelado  (US-042)  [PG+Rabbit]
- [US-042] **`eventoCancelado_cancelaInscricoesEIngressos`** **(CRITICO)**: evento 10 com 2 inscricoes `ATIVA` (c/ ingresso `ATIVO`) + 1 `PENDENTE_PAGAMENTO` (sem ingresso) → publica `evento.cancelado{eventoId=10}` (fila `evento.cancelado.ticket`) → apos consumo: 3 inscricoes `CANCELADA`; 2 ingressos `CANCELADO`; 1 `processed_events`.
- [US-042] `eventoCancelado_reentregue2x_cancelaApenas1Vez`: 2x mesma mensagem → estados finais identicos (inscricoes CANCELADA, ingressos CANCELADO), 1 `processed_events` (idempotente; UPDATE condicional `WHERE status IN (ATIVA,PENDENTE_PAGAMENTO)` ja nao casa na 2a).
- [US-042] `eventoCancelado_naoTocaInscricoesDeOutroEvento`: inscricao `ATIVA` do evento 20 permanece `ATIVA`.
- [US-042] `eventoCancelado_ingressoUtilizado_naoVoltaParaCancelado`: ingresso ja `UTILIZADO` (check-in 5B) nao e tocado (UPDATE so `status='ATIVO'`) — protege historico de check-in.
- [US-042 crit.1] `eventoCancelado_vagas`: (se PO aprovar reset no event-service) — assert no event-service que `vagasDisponiveis = capacidade` apos `cancelar`. No ticket nao ha chamada a liberar-vaga (ver architecture §Vagas).

### D2. Guard "evento tarde demais"  (ADR-T11 / CR-S4-01)  [PG+Rabbit]
- [US-042] `eventoCancelado_eventoSemInscricoes_ackNoop`: evento sem inscricoes → 0 linhas, ACK, `processed_events` gravado, sem DLQ.

---

## E. Frontend — Vitest + Testing Library  (opcional nesta trilha)
> So se a UI de "Encerrar evento" for incluida (PO decide). O extrato (`/payments/me`) ja existe; basta refletir os novos status.
- [US-043] `extrato_mostraStatusRepassado`: `GET /payments/me` retorna `status=REPASSADO` → badge "Repassado" + valor. (mock da API.)
- [US-042] `extrato_mostraStatusReembolsado`: `status=REEMBOLSADO` → badge "Reembolsado".
- [US-043] (se houver botao) `encerrarEvento_chamaPost_eMostraSucesso`: promotor dono clica "Encerrar" → `POST /api/events/:id/encerrar` → toast sucesso; status do evento vira "Realizado".

---

## Mapa de cobertura por criterio do PO
| Criterio PO | Caso(s) |
|---|---|
| US-043.1 (CONFIRMADO→REPASSADO, valor_repasse) | B2.a, B2.e |
| US-043.2 (Marina ve REPASSADO sem relogar) | B1.b, E (extrato) |
| US-043.3 (reentrega → 1x) | B2.b |
| US-043.4 (nao toca status != CONFIRMADO) | B2.c |
| US-042.1 (massa: REEMBOLSADO + reembolsos + inscricoes/ingressos CANCELADO + vagas) | C1.a, D1.a, D1.e |
| US-042.4 (reentrega evento.cancelado → 1x) | C1.b, D1.b |
| US-042.5 (admin lista REEMBOLSADO/REPASSADO) | C1.d |
| §7 corrida repasse-vs-reembolso | C2 |
| §7 reentrega processed_events | B2.b, C1.b, D1.b |
| §7 cancelar evento 2x | A4.b |
| TECH-S4-01 (evento_id/promotor_id) | B1.a, B1.b |
| afterCommit nao publica em rollback | A3.b, A4.c |

## Os 4 casos mais criticos (gate de aceite)
1. **B2.b** — `evento.finalizado` reentregue → repasse aplicado **1x** por pagamento (idempotencia, PG+Rabbit).
2. **C1.a + C1.b** — reembolso em massa pos `evento.cancelado` (3 pagamentos → REEMBOLSADO + 3 reembolsos) **e** idempotente na reentrega.
3. **C2** — corrida repasse-vs-reembolso no mesmo pagamento → **exatamente um** vence; nunca os dois (row lock + `WHERE status='CONFIRMADO'`).
4. **D1.a** — `evento.cancelado` cancela inscricoes + ingressos do evento (ticket), idempotente; ingresso UTILIZADO preservado.

## Infra de teste (reuso S4)
- `TestcontainersBase` singleton (PG `postgres:16-alpine` + Rabbit `rabbitmq:3.13-management` carregando `definitions.json` ja com `evento.cancelado`); `@Testcontainers(disabledWithoutDocker=true)`; `@ActiveProfiles("test-postgres")`.
- **event-service ganha um `TestcontainersBase` proprio** (1ª vez com Rabbit) — espelhar o do payment.
- `@BeforeEach` purga `evento.finalizado`, `evento.cancelado`, `evento.cancelado.ticket` (+ DLQs) para isolamento entre classes que compartilham o broker.
