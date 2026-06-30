# Sprint 4 — Code Review (saga de inscricao paga)

> Revisor (Principal Engineer, opus). Data: 2026-06-30. Branch: `feat/sprint-4-pagamento-escrow`.
> Diff revisado: `git diff main...feat/sprint-4-pagamento-escrow` — codigo de producao novo/alterado em
> payment-service, ticket-service, event-service e frontend (testes lidos como evidencia, nao revisados linha-a-linha).

## Resumo
- Arquivos de producao revisados: ~30 (back) + 6 (front).
- Achados: **P0 = 1 · P1 = 2 · P2 = 3 · P3 = 4**.
- **Corrigidos nesta passagem (com teste de regressao): CR-S4-01 (P0), CR-S4-02 (P1), CR-S4-03 (P1), CR-S4-04 (P1).**
- Suite local apos os fixes: ticket-service **38/38** (5 skip Docker), payment-service **15/15**, frontend **74/74**, `vite build` verde, reactor `test-compile` verde. Testcontainers (RabbitMQ+PG) **pulam local** (sem Docker) — validar no CI.
- **Veredicto: PRONTO PARA PR** — 0 P0/P1 em aberto. P2/P3 devolvidos ao owner (abaixo).

---

## Tabela de achados

| ID | Sev | Local | 1 linha |
|---|---|---|---|
| CR-S4-01 | **P0** | `ticket/messaging/PagamentoAprovadoListener.java:60` | `pagamento.aprovado` para inscricao nao-PENDENTE (R6: ja EXPIRADA) -> `ativar()` lanca -> rollback desfaz `processed_events` -> **poison message em loop -> DLQ**. **CORRIGIDO** (guard de estado + ACK no-op). |
| CR-S4-02 | **P1** | `ticket/service/ExpiracaoReservaJob.java:44` | Job `@Transactional` chama `eventClient.liberarVaga()` (HTTP) **dentro** da tx, segurando lock/conexao de banco durante N round-trips de rede. **CORRIGIDO** (estado em tx curta; HTTP fora da tx). |
| CR-S4-03 | **P1** | `payment/exception/GlobalExceptionHandler.java` | `GET /payments?status=foo` -> `StatusPagamento.valueOf` lanca `IllegalArgumentException` -> catch-all **500** (viola CR-S3-03 "cliente nunca 500"). Faltam handlers de type-mismatch/not-readable. **CORRIGIDO**. |
| CR-S4-04 | **P1** | `ticket/repository/IngressoRepository.java:27` | `/tickets/me` usa INNER JOIN Ingresso×Inscricao -> inscricao `PENDENTE_PAGAMENTO` (sem ingresso) **nunca aparece** -> card "aguardando pagamento" e UI morta em producao (US-041 crit.5 nao entregue de verdade; teste front so passava por mock). **CORRIGIDO** (LEFT JOIN + factory `fromPendente`). |
| CR-S4-05 | P2 | `payment/service/PagamentoService.java:113` | `confirmar` nao verifica estado da inscricao (DB-per-service) -> confirma pagamento de inscricao ja EXPIRADA e devolve 200 `CONFIRMADO` em vez de 409 `INSCRICAO_EXPIRADA` (contrato §2). Mitigado pelo guard CR-S4-01 (ticket nao emite ingresso), mas o contrato/UX diverge. Owner: Backend. |
| CR-S4-06 | P2 | `payment/service/PagamentoService.java:142` | `pagamento.aprovado.eventoId` sempre `null` (TECH-S4-01 ja conhecido) — payload incompleto; S4 ok (consumidor age por `inscricaoId`), mas o produtor emite contrato com campo nulo. Owner: Backend (S5). |
| CR-S4-07 | P2 | `payment/messaging/PagamentoAprovadoPublisher.java:40-48` + `service/PagamentoService.java:57-61` | Injecao acrobatica para acomodar testes (3 construtores no publisher; setter `@Autowired(required=false)` + guard `!= null` no service). Dois caminhos de runtime (com/sem repo/template) -> risco de no-op silencioso em prod mal-configurada. Owner: Backend (simplificar via construtor + perfil de teste). |
| CR-S4-08 | P3 | `ticket/messaging/PagamentoAprovadoListener.java:27` vs `payment/messaging/PedidoCriadoListener.java:18` | Gate do consumidor inconsistente entre servicos: `@Profile("!test")` (ticket) vs `@ConditionalOnBean(ConnectionFactory.class)` (payment). Ambos funcionam (`test`/`test-postgres`); padronizar. Owner: Backend. |
| CR-S4-09 | P3 | `ticket/...InscricaoService.java:148` vs `payment/...PagamentoAprovadoPublisher.java:64` | `isSynchronizationActive()` (ticket) vs `isActualTransactionActive()` (payment) para registrar `afterCommit`. O segundo e o mais correto; alinhar os dois. Owner: Backend. |
| CR-S4-10 | P3 | `ticket/messaging/PedidoCriadoPublisher.java` (sem ConfirmCallback) | Produtor publica via `convertAndSend` sem publisher-confirms; se o broker estiver fora **apos** o commit local, o evento se perde silenciosamente (gap aceitavel no escopo academico, mas registrar). Owner: Arquiteto (S5/observabilidade). |
| CR-S4-11 | P3 | `ticket/service/ExpiracaoReservaJob.java:43` | `fixedDelayString = job-delay-ms` (default 300000 = 5min) — TTL de 30min + job a cada 5min implica latencia de ate +5min para liberar a vaga. Documentar como aceitavel. Owner: Arquiteto. |

---

## P0 — Bloqueador (corrigido)

### CR-S4-01 — `pagamento.aprovado` em inscricao nao-PENDENTE vira poison message (Risco R6)
- **Local:** `services/ticket-service/src/main/java/com/ticketeira/ticket/messaging/PagamentoAprovadoListener.java`
- **Problema:** o `@RabbitListener @Transactional consumir()` gravava `processed_events` e chamava `inscricao.ativar()` **sem verificar o estado**. `ativar()` lanca `BusinessException` se o estado nao for `PENDENTE_PAGAMENTO`. Na corrida R6 (o `ExpiracaoReservaJob` expira a inscricao entre a confirmacao do pagamento e a entrega do `pagamento.aprovado`), a inscricao esta `EXPIRADA`:
  1. `processed_events` INSERT commitaria (primeira entrega),
  2. `ativar()` lanca,
  3. a excecao sai do metodo `@Transactional` -> **rollback** -> a linha de `processed_events` e desfeita,
  4. sem ACK -> RabbitMQ **re-entrega para sempre** -> apos N tentativas -> DLQ.
  A arquitetura (ADR-T10/R6) diz que a confirmacao tardia de uma EXPIRADA e um **no-op aceitavel** — nao um poison message. Tambem afetava o caso de inscricao ja `ATIVA` (2a entrega com `eventId` diferente: a rede UNIQUE de `ingressos.inscricao_id` so pega na emissao, mas `ativar()` ja teria lancado antes).
- **Correcao aplicada:** guard de estado dentro do listener — se `inscricao.getStatus() != PENDENTE_PAGAMENTO`, loga e **retorna** (ACK; `processed_events` permanece commitado; nenhum ingresso emitido). Mantem exactly-once-effect e evita a DLQ.
- **Teste de regressao:** `PagamentoAprovadoListenerGuardTest` (unit Mockito, roda local) — EXPIRADA/ATIVA -> nao emite, nao lanca; PENDENTE -> emite. + `PagamentoAprovadoListenerIntegrationTest#A3.d` (Testcontainers, CI).

---

## P1 — Importantes (corrigidos)

### CR-S4-02 — TTL job segura transacao de banco durante I/O HTTP
- **Local:** `services/ticket-service/src/main/java/com/ticketeira/ticket/service/ExpiracaoReservaJob.java`
- **Problema:** `executar()` era `@Transactional` e, no loop sobre as vencidas, chamava `eventClient.liberarVaga()` (RestClient/HTTP) **dentro** da tx. Isso mantinha a conexao + row locks das inscricoes abertos por toda a duracao de N chamadas de rede ao event-service (latencia/timeout segurando recurso de banco contendido). Anti-padrao explicito em `coding-standards.md` §Mensageria/Concorrencia ("listener nao segura tx aberta em I/O").
- **Correcao aplicada:** `executar()` deixa de ser `@Transactional`. A transicao `PENDENTE_PAGAMENTO -> EXPIRADA` e persistida por `inscricaoRepository.save()` (tx curta propria do Spring Data, sem I/O); a compensacao `liberarVaga()` (HTTP) so roda **apos** persistir o estado e **fora** de qualquer tx. Evitei deliberadamente um metodo `@Transactional` auto-invocado (a auto-invocacao furaria o proxy AOP do Spring — armadilha conhecida).
- **Teste:** `ExpiracaoReservaJobTest` (A4.a-d, Testcontainers) continua valido — comportamento observavel preservado (status EXPIRADA + `liberarVaga` 1x por vencida).

### CR-S4-03 — `payment` transforma erro de input do cliente em 500
- **Local:** `services/payment-service/src/main/java/com/ticketeira/payment/exception/GlobalExceptionHandler.java`
- **Problema:** o handler do payment nao tratava `MethodArgumentTypeMismatchException` (path/param malformado) nem o `IllegalArgumentException` de `StatusPagamento.valueOf(statusFiltro)` em `GET /payments?status=foo`. Ambos caiam no catch-all `Exception` -> **500**. Viola a regra ja consolidada (CR-S3-03 em `coding-standards.md`): "nenhum caminho de cliente vira 500". O ticket-service ja tinha esses handlers; o payment ficou para tras.
- **Correcao aplicada:** adicionados handlers de `MethodArgumentTypeMismatchException` (400), `IllegalArgumentException` (400) e `HttpMessageNotReadableException` (400). `BusinessException` (mais especifico) continua tendo precedencia.
- **Teste de regressao:** `GlobalExceptionHandlerWebTest` (standalone MockMvc, roda local) — `inscricaoId` nao-numerico -> 400; `status` invalido (IllegalArgumentException) -> 400.

### CR-S4-04 — `PENDENTE_PAGAMENTO` nunca aparece em "Meus ingressos" (INNER JOIN)
- **Local:** `services/ticket-service/src/main/java/com/ticketeira/ticket/repository/IngressoRepository.java` (`findIngressoComInscricaoByUsuarioId`)
- **Problema:** a query de `/tickets/me` fazia `INNER JOIN` entre `Ingresso` e `Inscricao`. Uma inscricao `PENDENTE_PAGAMENTO` **nao tem ingresso**, entao nunca era retornada. O frontend (`MeusIngressos.tsx` -> `IngressoPendenteCard`) e o criterio aceito **US-041 crit.5** ("Marina ve PENDENTE_PAGAMENTO vs ATIVA separado") dependem dessas linhas. O teste de front `MeusIngressosPendentes.test.tsx` so passava porque **mocka** `meusIngressos()` retornando a linha pendente — falso verde; em producao o card era UI morta.
- **Correcao aplicada:** query reescrita como `LEFT JOIN` dirigida por `Inscricao`, filtrando `status IN (ATIVA, PENDENTE_PAGAMENTO)`, ordenada por `inscritoEm DESC`. Novo factory `MeuIngressoResponse.fromPendente(inscricao)` (ingresso vazio/nulo, sem QR) — casa com o shape que o front ja trata (`ingressoId`/`codigoUnico` vazios).
- **Teste de regressao:** `TicketControllerIntegrationTest#meusIngressos_incluiPendentePagamentoSemIngresso` (H2, roda local) — `/tickets/me` retorna ATIVA (com QR) + PENDENTE_PAGAMENTO (codigoUnico vazio).

---

## O que esta correto e bem feito (resumo)
- **Idempotencia (ADR-T11):** `INSERT processed_events` na **mesma tx** do efeito, com PK UUID; `saveAndFlush` forca a colisao de PK *antes* do efeito, e `DataIntegrityViolationException` -> ACK no-op. Padrao identico nos dois consumidores. Rede final UNIQUE (`pagamentos.inscricao_id`, `ingressos.inscricao_id`) presente.
- **`afterCommit` (ADR-T11):** publicacao registrada via `TransactionSynchronization` dentro de tx ativa; com fallback para publicacao direta so quando nao ha tx (testes). Rollback nao publica — coberto por `*AfterCommitRollbackTest`.
- **Confirmacao concorrente (ADR-T11):** `findByInscricaoIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`) serializa; transicao `confirmar()` idempotente retorna boolean e **so publica se transicionou** (1 evento). Bem feito.
- **Reserva atomica (ADR-T07):** preservada no ramo PAGO — `reservarVaga` (UPDATE atomico) **antes** de criar a inscricao; compensacao `liberarVaga` limitada pela capacidade; ramo GRATUITO da S3 inalterado.
- **ADR-T08 (canal interno):** `/internal/events/{id}` exige `X-Internal-Token` com **comparacao constante-no-tempo** (`MessageDigest.isEqual`, null-safe); nao roteado pelo gateway; `EventClient` injeta o token e traduz 403/404/5xx em erros tipados (nunca 500 mudo). `EventoInternoResponse` expoe `preco`/`promotorId` so no canal interno. Correto.
- **Topologia AMQP:** `RabbitConfig` (ambos) declara exchange/filas/DLX/DLQ batendo com `infra/rabbitmq/definitions.json`. Consumidores nao sobem no perfil `test` (RabbitAutoConfiguration excluida) e sobem em `test-postgres` (broker real). CI-safe.
- **Escrow:** `Pagamento.pendente` calcula `valor_taxa = round(bruto*taxa, 2, HALF_UP)` e `valor_repasse = bruto - taxa`, retido (CONFIRMADO), sem liberar — exatamente o escopo S4 (ADR-T10).
- **Seguranca dos endpoints:** `GET /payments` exige `X-User-Papel == ADMIN` (403 senao); consultas conferem ownership (`usuarioId == X-User-Id`); confirmar exige ownership; nenhum dado sensivel em log. Paginacao com cap de 100.
- **Frontend:** checkout com 1 toque + polling (intervalo/timeout 60s) + cleanup de timers no unmount; estados de UI explicitos (recusado/timeout/erro/confirmado); trata `PAGAMENTO_RECUSADO`/`INSCRICAO_EXPIRADA`; tipos sem `any`, sem `console.log`.

---

## Pendentes para o owner (P2/P3)
- **Backend:** CR-S4-05 (confirm deveria 409 em inscricao expirada — gap de contrato, hoje so mitigado no ticket), CR-S4-06 (persistir `evento_id`/`promotor_id` — TECH-S4-01, S5), CR-S4-07 (simplificar a injecao do publisher/repo), CR-S4-08 (padronizar gate dos consumidores), CR-S4-09 (alinhar `isActualTransactionActive`).
- **Arquiteto:** CR-S4-10 (publisher-confirms / perda de evento pos-commit se broker fora — observabilidade S5), CR-S4-11 (documentar latencia TTL+job-delay).

## Recorrencia que vira/reforca regra
- **CR-S4-03 reincide CR-S3-03:** todo novo `GlobalExceptionHandler` de servico deve herdar o conjunto completo de handlers de borda (type-mismatch, not-readable, no-resource, illegal-argument). Sugiro extrair um `@RestControllerAdvice` base em `common-lib` para nao recriar (e esquecer handlers) por servico — proposta de regra em `coding-standards.md` §Backend/Erros.
