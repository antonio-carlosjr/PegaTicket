# Sprint 4 — Especificacao de Testes (TDD)

> Testes **vermelhos primeiro**. Cada caso marcado com a historia (US-040/041/060).
> Concorrencia/idempotencia/saga exigem **Testcontainers Postgres + RabbitMQ** (broker real — `@RabbitListener`/row lock nao sao exercitados por mock/H2).
> Padrao S3: `@Testcontainers(disabledWithoutDocker = true)` + `@ActiveProfiles("test-postgres")` -> **pula local sem Docker, roda no CI**.
> Cobertura: `PagamentoService` e ramo PAGO de `InscricaoService` >= 90%; resto >= 70%; front happy + 1 erro.

---

## Infra de teste (Testcontainers)

- **Postgres** (ja usado na S3): `PostgreSQLContainer<>("postgres:16-alpine")` + `@DynamicPropertySource` injeta `spring.datasource.*`, `spring.flyway.locations`, `ddl-auto=none`.
- **RabbitMQ** (novo): `RabbitMQContainer("rabbitmq:3.13-management")` carregando `infra/rabbitmq/definitions.json` (mesma topologia da prod) via `withCopyFileToContainer(...)` ou `withPluginsEnabled("rabbitmq_management")` + import; injeta `spring.rabbitmq.{host,port,username,password}`. **NAO** excluir `RabbitAutoConfiguration` nesses testes (precisamos do broker real).
- Testes de concorrencia que mockam o broker continuam excluindo `RabbitAutoConfiguration` (padrao S3).
- Helper: `TestcontainersBase` (abstrata) declarando ambos os containers `static` (reuso entre classes) — evita subir broker por classe.

---

## A. ticket-service — JUnit 5 + AssertJ + Testcontainers

### A1. InscricaoService.inscrever — ramo PAGO  (US-040, US-041)  [Postgres]
- [US-041] `inscrever_eventoPago_criaPendentePagamento_semIngresso`: `EventResumo(tipo=PAGO, status=PUBLICADO, preco=100)` -> `Inscricao.status=PENDENTE_PAGAMENTO`, `ingressoRepository.count()==0`, response.ingresso == null, response.pagamento.valor == 100.
- [US-041] `inscrever_eventoPago_publicaPedidoCriado_aposCommit` [Postgres+Rabbit]: apos `inscrever`, mensagem aparece na fila `pedido.criado` com `eventId != null`, `valor=100`, `inscricaoId` correto. (consumir da fila ou usar `@RabbitListener` de teste).
- [US-040] `inscrever_eventoPago_esgotado_lanca409`: `reservarVaga` (mock/real) devolve esgotado -> `EVENTO_ESGOTADO` (409), `inscricaoRepository.count()==0`, **nenhum** `pedido.criado` publicado.

### A2. CONCORRENCIA — ultima vaga em evento PAGO  (US-040.4)  [Postgres real, obrigatorio]
- [US-040] `concorrencia_ultimaVagaPaga_K_threads_1reserva_Kmenos1_409`: K=10 threads, 1 vaga. EventClient real apontando event-service em Testcontainers **ou** stub de reserva com `UPDATE atomico` no Postgres. Assert: exatamente **1** sucesso (`PENDENTE_PAGAMENTO` criada), **K-1** com `EVENTO_ESGOTADO` (409); `vagas_disponiveis==0`; **nenhuma** das K-1 publica `pedido.criado` nem cria pagamento. (espelha `InscricaoConcorrenciaTest` da S3, com ramo PAGO.) `@RepeatedTest(3)`.

### A3. Consumidor pagamento.aprovado -> emite ingresso  (US-041, US-060)  [Postgres+Rabbit]
- [US-041] `pagamentoAprovado_emiteIngresso_eAtivaInscricao`: dada `Inscricao(PENDENTE_PAGAMENTO)`, publica 1x `pagamento.aprovado` -> apos consumo: 1 `Ingresso(ATIVO)` com `codigoUnico`, `Inscricao.status=ATIVA`, 1 registro em `processed_events`.
- [US-060/US-041] `pagamentoAprovado_reentregue2x_emiteApenas1Ingresso` **(CRITICO)**: publica 2x a **mesma** mensagem (mesmo `eventId`) -> `ingressoRepository.count()==1`, 1 linha em `processed_events`. (Idempotencia via `processed_events` PK; `ingressos.inscricao_id UNIQUE` como rede final.)
- [US-041] `pagamentoAprovado_paraInscricaoInexistente_vaParaDLQ_naoQuebra`: inscricao ausente -> consumidor falha -> mensagem na `pagamento.aprovado.dlq`; sistema permanece consistente (0 ingressos).

### A4. Job de expiracao (TTL)  (ADR-T10, risco R1)  [Postgres]
- [US-040] `expiracaoJob_pendenteVencida_expiraEliberaVaga`: `Inscricao(PENDENTE_PAGAMENTO, inscritoEm = now-31min)`, TTL=30 -> apos job: `status=EXPIRADA`, `liberarVaga` chamado 1x.
- [US-040] `expiracaoJob_pendenteRecente_naoExpira`: `inscritoEm = now-5min` -> permanece `PENDENTE_PAGAMENTO`; `liberarVaga` nao chamado.
- [US-040] `expiracaoJob_idempotente_inscricaoJaAtiva_naoToca`: `status=ATIVA` -> ignorada.

### A5. Producao em afterCommit  (US-060.3)  [unit, sem broker]
- [US-060] `afterCommit_naoPublicaEmRollback` **(CRITICO)**: mockar `RabbitTemplate`; forcar exceção na tx local de `inscrever` (PAGO) apos `registerSynchronization` -> rollback -> verificar `rabbitTemplate.convertAndSend(...)` **nunca** chamado (`verifyNoInteractions`). Caminho feliz -> chamado 1x apos commit.

### A6. Regressao GRATUITO  (US-041.4)  [Postgres]
- [US-041] `inscrever_eventoGratuito_emiteIngressoImediato_intacto`: `tipo=GRATUITO` -> `Inscricao.status=ATIVA` + `Ingresso` na hora; **nenhum** `pedido.criado`. (re-roda os testes S3 sem alteracao.)

### A7. Auth do endpoint interno (event-service)  (ADR-T08)  [MockMvc/Integration]
- [US-040] `internalEvents_semToken_retorna403`: `GET /internal/events/1` sem `X-Internal-Token` -> 403 `ACESSO_INTERNO_NEGADO`.
- [US-040] `internalEvents_viaGateway_retorna404`: `GET /api/internal/events/1` no gateway -> 404 (rota nao existe). (teste no api-gateway, padrao S3 §C.)
- [US-040] `internalEvents_comToken_incluiPrecoEPromotor`: com token valido, evento PAGO -> response inclui `preco` e `promotorId` nao nulos.

---

## B. payment-service — JUnit 5 + AssertJ + Testcontainers

### B1. Consumidor pedido.criado -> cria Pagamento PENDENTE + escrow  (US-040, US-060)  [Postgres+Rabbit]
- [US-040] `pedidoCriado_criaPagamentoPendente_comEscrowComputado`: publica `pedido.criado{valor=100}` -> `Pagamento(PENDENTE)`, `valorBruto=100.00`, `valorTaxa=10.00`, `valorRepasse=90.00`; `status != CONFIRMADO`; 1 linha em `processed_events`.
- [US-060/US-040] `pedidoCriado_reentregue2x_criaApenas1Pagamento` **(CRITICO)**: 2x mesma mensagem (mesmo `eventId`) -> `pagamentoRepository.count()==1`, 1 linha em `processed_events`. (PK + `pagamentos.inscricao_id UNIQUE` rede final.)

### B2. Confirmar pagamento (gateway simulado + escrow)  (US-040)  [Postgres]
- [US-040] `confirmar_pendente_transicionaParaConfirmado_eRetem`: `Pagamento(PENDENTE)` -> `confirmar` -> `status=CONFIRMADO`, `gatewayPaymentId` preenchido, `processadoEm != null`; valores de escrow inalterados; **nenhum** repasse/reembolso registrado (US-040.3 / escrow puro).
- [US-040] `confirmar_publicaPagamentoAprovado_aposCommit` [Postgres+Rabbit]: apos confirmar, mensagem na fila `pagamento.aprovado` com `eventId != null`, `inscricaoId`/`pagamentoId` corretos.
- [US-040.5] `confirmar_2x_idempotente_naoRepublica` **(CRITICO)**: confirmar o mesmo `inscricaoId` 2x -> `status=CONFIRMADO` (no-op na 2a), **apenas 1** `pagamento.aprovado` publicado (assert na contagem da fila).
- [US-040] `confirmar_pagamentoDeOutroUsuario_lanca403`: `X-User-Id != pagamento.usuarioId` -> 403 `PAGAMENTO_DE_OUTRO_USUARIO`.
- [US-040] `confirmar_inscricaoSemPagamento_lanca404`: sem `Pagamento` para o `inscricaoId` -> 404 `PAGAMENTO_NAO_ENCONTRADO`.
- [US-040] `confirmar_gatewayRecusa_mantemPendente_lanca402`: gateway SIMULADO configurado para recusar -> `status` permanece `PENDENTE`, 402 `PAGAMENTO_RECUSADO`, **nenhum** `pagamento.aprovado`.

### B3. Valores de escrow (calculo)  (US-040.2)  [unit]
- [US-040] `escrow_arredondamento`: parametrizado (100.00->10.00/90.00; 99.99->10.00/89.99; 0.01->0.00/0.01). `RoundingMode.HALF_UP`, scale 2.

### B4. afterCommit (rollback)  (US-060.3)  [unit]
- [US-060] `confirmar_afterCommit_naoPublicaEmRollback`: forcar rollback na confirmacao -> `RabbitTemplate` nunca chamado.

### B5. Consultas / auth  (US-040.3)  [MockMvc]
- [US-040] `getInscricao_dono_retorna200` / `getInscricao_naoDono_retorna403`.
- [US-040] `getPayments_admin_listaComValores`: `X-User-Papel=ADMIN` -> lista com `valorBruto/valorTaxa/valorRepasse`. `getPayments_naoAdmin_403`. (Se gateway nao injeta papel -> marcar divida; ver ponto ao PO.)
- [US-040] `getMe_semHeader_401`.

---

## C. Frontend — Vitest + Testing Library

### C1. Checkout PAGO (happy path)  (US-040, US-041)
- [US-040] `inscricaoPaga_redirecionaCheckout_mostraPagamentoPendente`: ao inscrever em evento PAGO, vai para `/checkout/:id` exibindo "Pagamento pendente" e botao "Pagar".
- [US-040] `clicarPagar_chamaConfirmar_eMostraSucesso`: 1 clique -> `POST /api/payments/:id/confirmar`; toast de sucesso; UI passa a "aguardando confirmacao".
- [US-041] `aposPagar_pollingMostraIngressoEmitido`: mock de `GET /api/payments/inscricao/:id`/"Meus ingressos" passa a retornar ingresso -> o ingresso com QR aparece **sem reload manual** (risco R3 do PO).

### C2. Erros (US-040)
- [US-040] `gatewayRecusa_mostraMensagemAmigavel`: `confirmar` -> 402 `PAGAMENTO_RECUSADO` -> mensagem amigavel (nao stack trace), botao "Tentar de novo".
- [US-041] `inscricaoGratuita_naoPassaPorCheckout`: evento GRATUITO -> vai direto para "Meus ingressos" com ingresso (regressao S3).

### C3. Estados de "Meus ingressos"  (US-041.2)
- [US-041] `inscricaoPendente_semIngresso_mostraAguardando`: `PENDENTE_PAGAMENTO` sem ingresso -> "aguardando confirmacao de pagamento" (nao mostra QR).

---

## Mapa de cobertura por criterio do PO
| Criterio PO | Caso(s) |
|---|---|
| US-060.1 (2a entrega ignorada) | A3.b, B1.b |
| US-060.2 (CI Testcontainers Rabbit+PG) | infra + A2/A3/B1 |
| US-060.3 (rollback nao publica) | A5, B4 |
| US-040.1 (pendente sem ingresso) | A1.a, C1.a |
| US-040.2 (escrow 10%) | B2.a, B3 |
| US-040.3 (admin audita) | B5 |
| US-040.4 (concorrencia ultima vaga PAGO) | A2 |
| US-040.5 (confirmar 2x idempotente) | B2.c |
| US-041.1 (ingresso pos-pagamento) | A3.a, C1.c |
| US-041.2 (sem evento -> sem ingresso) | A3 (controle) , C3 |
| US-041.3 (idempotencia de ingresso) | A3.b |
| US-041.4 (GRATUITO intacto) | A6, C2.b |
| US-041.5 (Marina ve ATIVA so apos emissao) | A3.a + listagem por status |

## Os 4 casos mais criticos (gate de aceite)
1. **A2** — ultima vaga PAGO concorrente (Postgres real): 1 reserva, K-1 x 409, sem pagamento criado.
2. **A3.b** — `pagamento.aprovado` reentregue -> 1 ingresso so.
3. **B1.b** — `pedido.criado` reentregue -> 1 pagamento so.
4. **A5 / B4** — afterCommit nao publica em rollback.
