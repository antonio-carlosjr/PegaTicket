# Sprint 5A (Financeiro) — Code Review (Revisor, opus)

## Resumo
- Diff revisado: `git diff main...HEAD` — produção em event/payment/ticket/frontend + `infra/rabbitmq/definitions.json`.
- Arquivos de produção revisados: ~30. Achados: **P0=0 · P1=0 · P2=4 · P3=3**.
- Executável local rodado verde nos 3 módulos (Testcontainers pulam: daemon Docker rejeita o client Java aqui — mesmo padrão S4, saga só valida no CI):
  - event-service: `Tests run: 97, Failures: 0, Errors: 0, Skipped: 11` — **BUILD SUCCESS**
  - payment-service: `Tests run: 47, Failures: 0, Errors: 0, Skipped: 26` — **BUILD SUCCESS**
  - ticket-service: `Tests run: 61, Failures: 0, Errors: 0, Skipped: 18` — **BUILD SUCCESS**
- **Veredicto: PRONTO PARA PR** (0 P0/P1). Os P2/P3 são consistência/dívida menor, listados ao owner; nenhum bloqueia. A validação real da saga/idempotência/corrida/fan-out continua dependente do CI (Testcontainers).

---

## Caça prioritária — resultado

### 1. Refactor de `cancelar()` / ordem de argumentos — **CORRETO** (atenção redobrada pedida)
Verifiquei TODOS os call sites. A assinatura mudou de `cancelar(promotorId, eventoId)` → `cancelar(eventoId, promotorId)` (e `encerrar(eventoId, promotorId)` nasce nessa ordem).
- `EventService.java:76` `cancelar(Long eventoId, Long promotorId)` → corpo chama `carregarComOwnership(promotorId, eventoId)` com os nomes locais corretos. **OK.**
- `EventController.java:109` `eventService.cancelar(id, userId)` → `(eventoId=id, promotorId=userId)`. **OK** (antes era `cancelar(userId, id)` na assinatura antiga — coerente).
- `EventController.java:120` `eventService.encerrar(id, userId)` → `(eventoId=id, promotorId=userId)`. **OK.**
- Testes atualizados coerentemente: `EventServiceTest.java:134/149/201` (`cancelar(5L,1L)`, `cancelar(7L,1L)`), `EventoPublisherAfterCommitTest.java:78/107/139`.
- **Não há call site cross-service** de `cancelar/encerrar` (são endpoints REST, não clientes internos). Nenhum argumento invertido. `EventServiceTest` (15 verdes) prova a ordem.

### 2. Mensageria nova do event-service — **CORRETA** (onde o S4 quebrou)
- `RabbitConfig` (event): delega RabbitTemplate ao autoconfigure (sem bean próprio, sem `@ConditionalOnBean`). Declara exchanges/filas/bindings espelhando `definitions.json`. **OK.**
- `EventoPublisher`: publica **só em `afterCommit`** (`registerSynchronization`); `eventId` UUID gerado na origem; rollback não publica (validado por `EventoPublisherAfterCommitTest` + integração `EventoPublicacaoIntegrationTest`). **OK.**
- `application-test-postgres.yml`: **removido** o exclude de `RabbitAutoConfiguration` (listeners/publisher precisam do broker no perfil de integração). `application-test.yml` (H2) **mantém** o exclude; e TODO `@SpringBootTest("test")` faz `@MockBean EventoPublisher` → contexto sobe sem `RabbitTemplate`. **OK** (validado: `EventServiceApplicationTests` verde).
- **Converter sem `JavaTimeModule` explícito:** event e payment usam `new Jackson2JsonMessageConverter()` (no-arg). Funciona porque o no-arg delega a `Jackson2ObjectMapperBuilder.json().build()`, que faz `findModules` e registra `jackson-datatype-jsr310` (está no classpath via `common-lib`) + desabilita `WRITE_DATES_AS_TIMESTAMPS`. → OffsetDateTime serializa ISO-8601. **Funciona, mas implícito** (ver CR-5A-01).

### 3. Fan-out `evento.cancelado` — **CORRETO**
`definitions.json` tem as 2 filas com o MESMO routing key `evento.cancelado`:
- `evento.cancelado` (payment) + `evento.cancelado.ticket` (ticket), cada uma com binding próprio em `ticketeira.events` (rk `evento.cancelado`) e DLQ dedicada. Filas distintas ⇒ cópia para cada (sem competing-consumers). **OK.**
- Cada serviço declara só a SUA fila de consumo: payment `RabbitConfig` declara `evento.cancelado` (+ `evento.finalizado`); ticket `config/RabbitConfig` declara `evento.cancelado.ticket`. event declara as 3 (só p/ RabbitAdmin idempotente/purge nos testes). **OK.**

### 4. Idempotência das sagas — **CORRETA**
- Repasse (`EventoFinalizadoListener`) e reembolso (`EventoCanceladoListener`) inserem `processed_events` na MESMA tx do efeito; `DataIntegrityViolationException` na PK → `return` (ACK no-op), **não lança** → sem poison message (CR-S4-01). **OK.**
- Repasse: 1 `UPDATE` condicional `WHERE status='CONFIRMADO'` (`repassarConfirmadosDoEvento`) — pagamento não-CONFIRMADO não é tocado (0 linhas = ACK normal). **OK.**
- Reembolso: `SELECT ... FOR UPDATE` só dos CONFIRMADO; na 2ª entrega o SELECT já não traz nada → 0 reembolsos extras. **OK.**
- ticket `EventoCanceladoListener`: `INSERT processed_events` + 2 UPDATEs em massa condicionais; reentrega não casa (status já CANCELADA/CANCELADO). **OK.**

### 5. Corrida repasse-vs-reembolso — **CORRETA**
Ambas as transições são condicionais em `status='CONFIRMADO'`; o repasse usa `@Modifying UPDATE ... WHERE status=CONFIRMADO` (row lock) e o reembolso `SELECT ... FOR UPDATE`. O row lock do Postgres serializa as duas tx → exatamente 1 vence, o outro acha 0 linhas. Teste `CorridaRepasseReembolsoTest` (`@RepeatedTest(3)`) cobre (pula local; roda no CI). **OK** — sem janela identificada.

### 6. O(n²)/N+1 — **OK**
- Repasse = 1 UPDATE em massa. Reembolso = 1 SELECT FOR UPDATE + loop de INSERT (1 reembolso/linha; sem N+1 no SELECT — aceitável). ticket = 2 UPDATEs em massa (subquery por `evento_id`, sem loop por linha). Índices: `idx_pagamentos_evento` (V3), `idx_inscricoes_evento` (V1).

### 7. Transações/recursos — **OK**
- Listeners não fazem I/O de rede dentro da tx (só DB). `@Modifying(clearAutomatically=true)` presente em todas as queries de UPDATE em massa (repasse, cancelar inscrições/ingressos). Reembolso (saves de entidade) não precisa de clear.

### 8. Segurança — **OK**
- `POST /events/{id}/encerrar` exige `requireUserId` + `requirePromotor(papel)` + `carregarComOwnership` — idêntico a `cancelar`. (`EncerrarEventoControllerTest`: 401/403/404/409/200 cobertos, 6 verdes.)
- Refactor `cancelar` não inverteu argumentos em nenhum site (ver item 1).
- Sem PII nos payloads AMQP (`eventId`/`eventoId`/`promotorId`/`ocorridoEm`); sem segredo em log.

### 9. Dinheiro — **OK**
- `Reembolso.criar` faz `valor.setScale(2, HALF_UP)`; `Pagamento.pendente` idem nos 3 valores. `PagamentoResponse` tem `@JsonFormat(shape=STRING)` em `valorBruto/valorTaxa/valorRepasse`.

### 10. Migrations — **OK**
- payment `V3__repasse_reembolso.sql`: colunas NULLABLE (`evento_id`,`promotor_id`,`repassado_em`,`reembolsado_em`) + `idx_pagamentos_evento` + `uk_reembolso_evento_cancelado` (índice parcial; PO aprovou RA1). Compatível com `ddl-auto: validate` (Hibernate valida colunas, não índices). Entidade `Pagamento` mapeia todas.
- event `V3__realizado_cancelado.sql`: `realizado_em`,`cancelado_em` NULLABLE; entidade `Evento` mapeia.
- ticket: sem migration (status já existem). **OK.**

---

## P2 — Importantes (não bloqueiam; owner decide)

### CR-5A-01 — Converter JSON depende de auto-detecção de módulo (inconsistência entre serviços)
- **Sev:** P2 · **Local:** `event-service/.../messaging/RabbitConfig.java:41-43`, `payment-service/.../messaging/RabbitConfig.java:38-41` (no-arg) **vs** `ticket-service/.../config/RabbitConfig.java:140-146` (explícito com `JavaTimeModule` + `disable(WRITE_DATES_AS_TIMESTAMPS)`).
- **Por que:** event/payment usam `new Jackson2JsonMessageConverter()` (sem ObjectMapper explícito). Funciona hoje porque o no-arg delega ao `Jackson2ObjectMapperBuilder` que faz `findModules` e jsr310 está no classpath (`common-lib`). Mas é **implícito**: se algum serviço perder o jsr310 transitivo, `OffsetDateTime` quebraria silenciosamente em runtime (não em teste H2). A spec (architecture §Padrão de RabbitConfig / tests-spec) pede o converter **com `JavaTimeModule`** explícito. ticket faz certo; event/payment não.
- **Correção sugerida (owner):** padronizar os 3: construir `Jackson2JsonMessageConverter(mapper)` com `mapper.registerModule(new JavaTimeModule()).disable(WRITE_DATES_AS_TIMESTAMPS)` (copiar o bean do ticket). Não apliquei: muda o gabarito dos 3 serviços e o atual está funcional/verde — decisão de padronização do owner. Baixo risco, alto valor de consistência.

### CR-5A-02 — ticket-service: `@Profile("!test")` em config/listener novos (contraria a regra da própria spec)
- **Sev:** P2 · **Local:** `ticket-service/.../messaging/EventoCanceladoListener.java:23` (`@Profile("!test")`) e `ticket-service/.../config/RabbitConfig.java:24` (`@Profile("!test")`).
- **Por que:** architecture §Padrão de RabbitConfig (S4, obrigatório) diz **"Nao usar `@Profile("!test")` na config nova"** — o padrão canônico é a config inerte sem broker (como payment, que NÃO usa `@Profile` e passa o context-test em H2). ticket diverge: depende de o teste de integração usar perfil `test-postgres` (não `test`) para o `!test` ativar. Funciona, mas é frágil e inconsistente com payment/event; reintroduz exatamente o estilo que custou os 6 ciclos do S4.
- **Correção sugerida (owner):** remover `@Profile("!test")` de ambos e confiar na config inerte (o ticket `RabbitConfig` já era assim no S4 — é dívida pré-existente que a 5A propaga ao novo listener). Não apliquei: remover `@Profile` exige revalidar o perfil `test` (H2) do ticket sobe sem `RabbitTemplate` — precisa do mesmo cuidado de `@MockBean`/injeção opcional do payment; mudança não-trivial, melhor o owner (Backend) fazer com a suíte de integração no CI à mão.

### CR-5A-03 — Métodos de domínio `cancelarPorEvento()`/`Ingresso.cancelar()` são código morto em produção
- **Sev:** P2 · **Local:** `ticket-service/.../domain/Inscricao.java:87` (`cancelarPorEvento`) e `.../domain/Ingresso.java:64` (`cancelar`).
- **Por que:** o `EventoCanceladoListener` (ticket) usa as queries `@Modifying` em massa (`cancelarInscricoesDoEvento`/`cancelarIngressosDoEvento`), **não** os métodos de entidade. `cancelarPorEvento()` e `Ingresso.cancelar()` só são referenciados por `InscricaoCancelamentoPorEventoTest`. Coding-standards §0.3: "Sem código morto". A data-model.md propôs ambos os caminhos; a impl (corretamente) escolheu o bulk, mas deixou os métodos órfãos.
- **Correção sugerida (owner):** ou remover os 2 métodos + o teste unitário, OU mantê-los conscientemente como API reusável para 5B (`CANCELAMENTO_PARTICIPANTE`), documentando no backend-log que são intencionalmente pré-construídos para 5B. Não removi: há sinal de que são reuso planejado p/ 5B (motivo parametrizado, mesma família de `reembolsar()`); decisão de escopo do owner.

### CR-5A-04 — Violação do índice parcial `uk_reembolso_evento_cancelado` não é tratada no listener
- **Sev:** P2 (defesa-em-profundidade com aresta) · **Local:** `payment-service/.../messaging/EventoCanceladoListener.java:67-73`.
- **Por que:** o `reembolsoRepository.save(reembolso)` pode lançar `DataIntegrityViolationException` se o índice parcial `uk_reembolso_evento_cancelado` (1 reembolso EVENTO_CANCELADO por pagamento) for violado. Esse `save` está **fora** do try/catch da idempotência (que cobre só o `processed_events`). Em fluxo normal é inalcançável (o SELECT `WHERE status='CONFIRMADO'` + `processed_events` já barram a 2ª entrega). Mas num reprocessamento manual com `eventId` diferente, a violação **propagaria** → rollback → reentrega → poison message (o mesmo padrão CR-S4-01 que o índice tentava prevenir vira o gatilho do poison).
- **Correção sugerida (owner):** se o índice parcial for mantido (PO aprovou RA1), envolver o `reembolsoRepository.save` (ou o loop) capturando `DataIntegrityViolationException` como ACK no-op idempotente, OU confiar só no `processed_events` e tornar o índice realmente inofensivo. Probabilidade baixíssima (exige reprocesso manual fora do `processed_events`); não apliquei para não alterar a semântica de erro sem o owner decidir entre "índice + catch" vs "só processed_events".

---

## P3 — Menores (nota)

### CR-5A-05 — `EventoPublisher` (event) chama `registerSynchronization` sem guardar `isActualTransactionActive()`
- **Local:** `event-service/.../messaging/EventoPublisher.java:41,66`.
- **Por que:** diferente do `PagamentoAprovadoPublisher` (payment), que faz `if (isActualTransactionActive()) {...} else {publica direto}`. Se `publicarEventoFinalizado/Cancelado` fosse chamado fora de tx, lançaria `IllegalStateException`. Em produção é sempre invocado dentro de `@Transactional` (EventService), então é inofensivo — mas é mais frágil que o gabarito do payment. P3.

### CR-5A-06 — Teste "CRÍTICO" de afterCommit-rollback mocka o próprio publisher
- **Local:** `event-service/.../messaging/EventoPublisherAfterCommitTest.java:46,84-86`.
- **Por que:** o teste rotulado CRÍTICO mocka `EventoPublisher` e só verifica que o serviço não chama o publisher quando `save()` lança — **não** exercita o `afterCommit` real (o `registerSynchronization` que de fato protege contra publicar em rollback). Essa garantia fica só no `EventoPublicacaoIntegrationTest` (Testcontainers, skip local). O nome supervaloriza a cobertura unitária. P3 (rigor de teste; a integração cobre o caminho real).

### CR-5A-07 — `EventoCanceladoEvent`/`EventoFinalizadoEvent` triplicados (event/payment/ticket)
- **Local:** 3 cópias do mesmo record por serviço (DB-per-service ⇒ esperado; sem FK/lib compartilhada de payload). Aceitável pela arquitetura (acoplamento via contrato, não via classe), mas vale nota: divergência de campo entre cópias passaria despercebida em compilação. Conferido: os 4 records (event×2, payment×2, ticket×1) têm os mesmos campos/ordem. P3.

---

## O que está correto (destaques)
- Refactor `cancelar(eventoId, promotorId)` consistente em assinatura + controller + 3 testes (atenção pedida — sem bug).
- Fan-out `evento.cancelado` com 2 filas independentes + DLQs no `definitions.json` e nas RabbitConfigs — sem competing-consumers.
- Idempotência via `processed_events` na mesma tx + ACK no-op em PK colidida (sem poison message) nos 3 consumidores.
- Transições condicionais `WHERE status='CONFIRMADO'` + row lock (UPDATE em massa / SELECT FOR UPDATE) garantem 1 vencedor na corrida.
- afterCommit no event-service (rollback não publica); `eventId` UUID na origem.
- Dinheiro com `setScale(2, HALF_UP)` + `@JsonFormat(STRING)`.
- Migrations idempotentes, NULLABLE corretas, compatíveis com `ddl-auto: validate`.
- Frontend: `encerrarEvento` tipado, botão "Encerrar" só em PUBLICADO, status REPASSADO/REEMBOLSADO no extrato, sem `any`/`console.log`.

## Itens NÃO aplicados (e por quê)
Nenhum P0/P1 existe, então **nenhuma correção foi aplicada** ao working tree (a tarefa manda aplicar P0/P1 claros; P2/P3 vão ao owner). Build local permanece verde nos 3 módulos. CR-5A-01/02/03/04 ficam para Backend decidir (padronização/dívida/escopo 5B). Nada commitado, nada pushed, sem PR.
