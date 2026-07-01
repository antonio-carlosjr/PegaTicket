# Sprint 5 — Trilha 5B (Experiencia) — Especificacao de Testes (TDD)

> Tester escreve VERMELHO antes do Back. JUnit 5 + AssertJ. Unit/controller em H2 (perfil `test`, Rabbit excluido). Integracao de saga em Postgres+RabbitMQ reais (`@ActiveProfiles("test-postgres")`, `TestcontainersBase` SINGLETON, `disabledWithoutDocker=true`).
> Cada caso marcado com a historia. Convencao de classe/metodo segue os gabaritos existentes (`EncerrarEventoControllerTest`, `EventoCanceladoListenerIntegrationTest`, `InternalEventControllerTest`).

## Aprendizados S4/5A OBRIGATORIOS (todo teste de integracao com broker)
- **`TestcontainersBase` SINGLETON:** containers `static` iniciados em bloco `static` guardado por `DockerClientFactory.instance().isDockerAvailable()`, **sem `@Container`**. Reusar a base existente de cada servico (ticket/event/payment ja a tem). Nunca recriar com `@Container` (para o container ao fim da 1a classe -> HikariPool total=0 / listener 30s timeout).
- **`@BeforeEach` purga as filas** novas (`rabbitAdmin.purgeQueue("inscricao.cancelada", false)`) + limpa repos. Broker/contexto sao cacheados entre testes.
- **Perfil com broker NAO exclui `RabbitAutoConfiguration`** (`test-postgres`); Postgres-only excluem via `@DynamicPropertySource`/`spring.autoconfigure.exclude` e mockam o publisher.
- **`RabbitConfig` delega ao autoconfigure** (so `MessageConverter`+filas/bindings; sem `RabbitTemplate` proprio; sem `@ConditionalOnBean(ConnectionFactory.class)`).
- **Consumidor:** ACK no-op em estado inesperado (CR-S4-01) — `inscricao.cancelada` para pagamento ausente/nao-CONFIRMADO **nunca** lanca/poison.
- **Dinheiro:** `setScale(2)` no `Reembolso.criar` (ja); asserts de valor com `BigDecimal` compareTo.

---

## A. ticket-service — Check-in (US-034)

### A1. `IngressoRealizarCheckinTest` (unit/domain, H2 nao necessario — POJO)  [US-034]
- A1.a ✅ `realizarCheckin` em ingresso ATIVO -> status UTILIZADO.  **[US-034]**
- A1.b ✅ `realizarCheckin` em ingresso ja UTILIZADO -> lanca `BusinessException("INGRESSO_JA_UTILIZADO", 409)`.  **[US-034.2]**
- A1.c ✅ `realizarCheckin` em ingresso CANCELADO -> lanca 409 (nao reaproveita cancelado).  **[US-034.4]**
- A1.d ✅ `utilizar()` (5A) permanece no-op idempotente — nao quebrado (fixtures da 5A).  **[US-034 / regressao 5A]**

### A2. `CheckinControllerTest` (MockMvc, `@ActiveProfiles("test")`, H2; `EventClient` @MockBean)  [US-034]
> Gabarito: `EncerrarEventoControllerTest`. Seed: cria inscricao+ingresso ATIVO direto via repo; mocka `eventClient.getEvento(eventoId)` retornando `EventResumo` com `promotorId` controlado.
- A2.a ✅ PROMOTOR dono + ingresso ATIVO -> 200, body `status=UTILIZADO`, cria `checkins` (count=1).  **[US-034.1]**
- A2.b ✅ 2a leitura do mesmo QR -> 409 `INGRESSO_JA_UTILIZADO`; `checkins` count permanece 1.  **[US-034.2]**
- A2.c ✅ PROMOTOR **nao-dono** (`evento.promotorId != userId`) -> 403 `CHECKIN_EVENTO_ALHEIO`; ingresso permanece ATIVO.  **[US-034.3]**
- A2.d ✅ `codigo_unico` inexistente -> 404 `INGRESSO_NAO_ENCONTRADO`.  **[US-034.4]**
- A2.e ✅ ingresso CANCELADO -> 404 `INGRESSO_NAO_ENCONTRADO`.  **[US-034.4]**
- A2.f ✅ papel PARTICIPANTE -> 403 `Acesso restrito a promotores.`  **[US-034.5]**
- A2.g ✅ sem `X-User-Id` -> 401.  **[US-034 / auth]**
- A2.h ✅ body sem `codigoUnico` (blank) -> 400.  **[borda]**

### A3. `CheckinConcorrenciaTest` (Testcontainers PG, `test-postgres`)  [US-034.6] CRITICO
> 2 threads fazem check-in do MESMO ingresso ATIVO simultaneamente.
- A3.a ✅ **CRITICO:** N=2 check-ins simultaneos do mesmo `codigo_unico` -> **exatamente 1** retorna 200 (UTILIZADO) e **1** retorna 409; `checkins` count=1; ingresso UTILIZADO.  **[US-034.6]**
  - Estrategia testada: `UNIQUE(ingresso_id)` em `checkins` + transicao condicional. Em Postgres real (H2 nao reproduz o row lock — mesmo racional do ADR-T07).

---

## B. ticket-service — Cancelamento (US-035) + gatilho do reembolso

### B1. `CancelamentoInscricaoServiceTest` (unit, mocks de EventClient/publisher/repos)  [US-035]
> `eventClient.getEvento` mockado p/ controlar `tipo`, `dataInicio`, `prazoReembolsoDias`.
- B1.a ✅ evento **GRATUITO**: cancela -> inscricao CANCELADA + ingresso CANCELADO + `liberarVaga` chamado; **nao** publica `inscricao.cancelada`; `reembolsoIniciado=false`.  **[US-035.1]**
- B1.b ✅ evento **PAGO dentro do prazo**: cancela -> CANCELADA + ingresso CANCELADO + `liberarVaga` + **publica** `inscricao.cancelada` (afterCommit); `reembolsoIniciado=true`.  **[US-035.2 / US-042 individual]**
- B1.c ✅ evento **PAGO fora do prazo**: -> lanca 422 `PRAZO_CANCELAMENTO_ENCERRADO`; inscricao permanece ATIVA; **nada** publicado; `liberarVaga` NAO chamado.  **[US-035.3 / PO-D2]**
- B1.d ✅ inscricao de outro usuario (`usuarioId != X-User-Id`) -> 403 `CANCELAMENTO_DE_OUTRO`; sem mutacao.  **[US-035.4]**
- B1.e ✅ inscricao ja CANCELADA -> 409 `INSCRICAO_JA_CANCELADA` (rowsAffected=0).  **[US-035.5]**
- B1.f ✅ inscricao inexistente -> 404 `INSCRICAO_NAO_ENCONTRADA`.  **[borda]**
- B1.g ✅ borda do prazo: `data_inicio - prazo_dias == now` (limite exato) -> dentro do prazo (publica). `data_inicio` ja passou -> fora (422).  **[US-035.3]**

### B2. `CancelamentoControllerTest` (MockMvc, H2; EventClient/publisher @MockBean)  [US-035]
- B2.a ✅ DELETE inscricao gratuita propria -> 200, `status=CANCELADA`, `reembolsoIniciado=false`.  **[US-035.1]**
- B2.b ✅ DELETE inscricao paga dentro do prazo -> 200, `reembolsoIniciado=true`.  **[US-035.2]**
- B2.c ✅ DELETE inscricao paga fora do prazo -> 422 `PRAZO_CANCELAMENTO_ENCERRADO`.  **[US-035.3]**
- B2.d ✅ DELETE inscricao de outro -> 403.  **[US-035.4]**
- B2.e ✅ DELETE sem `X-User-Id` -> 401.  **[auth]**
- B2.f ✅ DELETE `{id}` nao-numerico -> 400.  **[borda]**

### B3. `CancelamentoConcorrenciaTest` (Testcontainers PG, `test-postgres`)  [US-035.5] CRITICO
- B3.a ✅ **CRITICO:** 2 DELETE simultaneos da mesma inscricao -> **exatamente 1** 200 e **1** 409 `INSCRICAO_JA_CANCELADA`; `liberarVaga` chamado **1x** (vaga liberada uma so vez).  **[US-035.5]**

### B4. `InscricaoCanceladaAfterCommitTest` (Testcontainers PG+Rabbit, `test-postgres`)  [US-035]
> Verifica que a publicacao e afterCommit (rollback nao publica) — espelha `InscricaoAfterCommitRollbackTest` da S4.
- B4.a ✅ commit OK -> mensagem `inscricao.cancelada` chega na fila (capturada via consumidor de teste ou `rabbitTemplate.receive`).  **[US-035 / ADR-T11]**
- B4.b ✅ rollback da tx local -> **nenhuma** mensagem publicada.  **[US-035 / ADR-T11]**

---

## C. payment-service — Reembolso individual (US-035 pago) — Testcontainers PG+Rabbit

### C1. `InscricaoCanceladaListenerIntegrationTest` (`test-postgres`, `TestcontainersBase`)  [US-035 / US-042 individual] CRITICO
> Gabarito: `EventoCanceladoListener*Test` da 5A. `@BeforeEach` purga `inscricao.cancelada` + limpa pagamentos/reembolsos/processed_events. Seed: cria `Pagamento` via factory + confirma (status CONFIRMADO) com `inscricao_id` conhecido.
- C1.a ✅ **CRITICO:** pagamento CONFIRMADO da inscricaoId -> publica `inscricao.cancelada` -> pagamento `REEMBOLSADO` + 1 `reembolsos(motivo='CANCELAMENTO_PARTICIPANTE', status='PROCESSADO', valor=valorBruto)` + 1 `processed_event`.  **[US-035.2]**
- C1.b ✅ **idempotente:** reentrega 2x da mesma mensagem -> 1 reembolso, pagamento REEMBOLSADO uma vez, `processed_events.count=1`.  **[US-035 / ADR-T11]**
- C1.c ✅ pagamento ja REEMBOLSADO (nao-CONFIRMADO) -> ACK no-op, **nenhum** novo reembolso (CR-S4-01).  **[US-035 / CR-S4-01]**
- C1.d ✅ inscricaoId **sem pagamento** (evento gratuito; defesa) -> ACK no-op, `processed_event` gravado, 0 reembolsos, sem DLQ.  **[US-035.1 / defesa]**
- C1.e ✅ pagamento PENDENTE (nunca confirmado) -> no-op (so CONFIRMADO reembolsa).  **[US-035 / borda]**

### C2. `ReembolsoIndividualVsMassaConcorrenciaTest` (`test-postgres`) — corrida rara  [US-035 + US-042]
- C2.a ✅ mesmo pagamento CONFIRMADO recebe `inscricao.cancelada` (individual) E `evento.cancelado` (massa) quase juntos -> **exatamente 1** reembolso aplicado (transicao condicional `reembolsar()` + lock pessimista); pagamento termina REEMBOLSADO uma vez; nunca 2 registros de reembolso para o mesmo pagamento na mesma corrida.  **[US-035 / corrida]**
  > Aceita-se que motivos podem diferir (`CANCELAMENTO_PARTICIPANTE` vs `EVENTO_CANCELADO`) conforme quem vence; o invariante e **1 unico estorno** por pagamento.

---

## D. event-service — Avaliacao (US-024)

### D1. `AvaliacaoControllerTest` (MockMvc, H2; `TicketClient` @MockBean)  [US-024]
> Seed evento via repo; controla `evento.status` (REALIZADO/PUBLICADO) e `ticketClient.participou(...)` (true/false).
- D1.a ✅ evento REALIZADO + `participou=true` + nota 4 -> 201 `AvaliacaoResponse`; grava `avaliacoes`.  **[US-024.1]**
- D1.b ✅ 2a avaliacao mesmo usuario+evento -> 409 `AVALIACAO_DUPLICADA` (UNIQUE).  **[US-024.2]**
- D1.c ✅ evento REALIZADO + `participou=false` -> 403 `AVALIACAO_NAO_ELEGIVEL`.  **[US-024.3]**
- D1.d ✅ evento NAO REALIZADO (PUBLICADO) -> 403 `AVALIACAO_NAO_ELEGIVEL` (pre-filtro local; nem chama o ticket).  **[US-024.3]**
- D1.e ✅ nota=0 ou nota=6 -> 400 (Bean Validation `@Min/@Max`).  **[US-024.4]**
- D1.f ✅ admin/promotor nao-participante (`participou=false`) -> 403.  **[US-024.5]**
- D1.g ✅ sem `X-User-Id` -> 401.  **[auth]**
- D1.h ✅ evento inexistente -> 404 `Evento nao encontrado.`  **[borda]**

### D2. `AvaliacaoElegibilidadeTest` (unit do AvaliacaoService, mocks)  [US-024]
- D2.a ✅ chama `ticketClient.participou` **apenas** quando `evento.status==REALIZADO` (pre-filtro evita round-trip).  **[US-024.3 / perf]**
- D2.b ✅ `ticketClient` retorna 503/erro -> `AvaliacaoService` propaga `TICKET_INDISPONIVEL` (503), **nunca 500** (falha fechada).  **[US-024 / resiliencia]**

### D3. `InternalTicketControllerTest` (ticket-service, MockMvc, H2)  [US-024]
> Gabarito: `InternalEventControllerTest`. Token `test-internal-secret`.
- D3.a ✅ GET `/internal/tickets/participou` sem `X-Internal-Token` -> 403 `ACESSO_INTERNO_NEGADO`.  **[US-024 / ADR-T08]**
- D3.b ✅ com token: usuario com Ingresso UTILIZADO no evento -> `{participou:true}`.  **[US-024 / PO-D1]**
- D3.c ✅ com token: usuario com Inscricao ATIVA no evento -> `{participou:true}`.  **[US-024 / PO-D1]**
- D3.d ✅ com token: usuario com Inscricao CANCELADA (e sem ingresso utilizado) -> `{participou:false}`.  **[US-024 / PO-D1]**
- D3.e ✅ com token: usuario sem vinculo -> `{participou:false}`.  **[US-024]**
- D3.f ✅ `usuarioId`/`eventoId` ausente ou nao-numerico -> 400 (nunca 500).  **[borda]**

### D4. `TicketClientTest` (event-service, WireMock ou MockRestServiceServer)  [US-024]
- D4.a ✅ `participou` envia `X-Internal-Token` e mapeia `{participou:true}` -> true.  **[US-024 / ADR-T08]**
- D4.b ✅ ticket responde 403 -> log de mis-config + lanca `TICKET_INDISPONIVEL`/falha fechada (nao 500).  **[US-024 / resiliencia]**

---

## E. event-service — Reputacao (US-025)

### E1. `ReputacaoTest` (integration repo/service, H2 ou Testcontainers PG)  [US-025]
- E1.a ✅ evento com 3 avaliacoes (notas 5,4,3) -> `GET /events/{id}` `reputacao={media:4.0,total:3}`.  **[US-025.1]**
- E1.b ✅ evento **sem** avaliacoes -> `reputacao={media:null,total:0}`.  **[US-025.1]**
- E1.c ✅ nova avaliacao reflete imediatamente no detalhe (sem cache) — media recalculada.  **[US-025.2]**
- E1.d ✅ `GET /events/{id}` por qualquer autenticado expoe a reputacao (nao restrito).  **[US-025.3]**
- E1.e ✅ query agregada e **1 round-trip** (sem N+1) — assert via contagem de queries (Hibernate stats) ou revisao.  **[US-025 / perf]**

---

## F. Regressao (nao quebrar 5A/S4)
- F1 ✅ `EventResumo`/`EventoInternoResponse` com 2 campos novos: fixtures existentes (`InternalEventAuthTest`, `InscricaoPagaServiceTest`, etc.) atualizadas para a nova aridade do record; testes da 5A continuam verdes.  **[regressao]**
- F2 ✅ `evento.cancelado` (massa, 5A) intacto: cancelar evento ainda cancela inscricoes/ingressos e reembolsa em massa.  **[regressao US-042]**
- F3 ✅ `utilizar()` (5A) preservado; `seedInscricaoComIngressoUtilizado` (fixture 5A) continua funcionando.  **[regressao]**

---

## Cobertura minima
- Services criticos (CheckinService, CancelamentoInscricaoService, reembolso individual no payment, AvaliacaoService): **>=90%**.
- Reputacao/InternalTicketController/TicketClient: **>=80%**.
- Concorrencia obrigatoria (Postgres real): A3 (duplo check-in), B3 (duplo cancelamento), C2 (corrida individual-vs-massa).
- Idempotencia obrigatoria (Testcontainers): C1.b (reentrega `inscricao.cancelada`).

## 4 casos mais criticos (gate da banca)
1. **A3.a** duplo check-in simultaneo -> 1 sucesso / 1 409, `checkins` count=1 (anti-duplo, US-034.6).
2. **C1.a + C1.b** reembolso individual CONFIRMADO -> REEMBOLSADO + `reembolsos(CANCELAMENTO_PARTICIPANTE)`, idempotente na reentrega (US-035.2).
3. **B1.c / B2.c** cancelamento pago fora do prazo -> 422, inscricao permanece ATIVA, sem reembolso (PO-D2, US-035.3).
4. **D1.b + D1.c** avaliacao: elegivel 201, 2a 409, nao-elegivel 403 (US-024.1/.2/.3).
