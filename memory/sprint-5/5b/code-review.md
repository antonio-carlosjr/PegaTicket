# Sprint 5 · Trilha 5B (Experiencia) — Code Review

> Revisor (opus), Passo 2 de `/validar-sprint 5b`. Diff de producao `git diff main...HEAD -- "services/**/src/main/**" "frontend/src/**"` (~39 arquivos, 4 US: US-034 check-in QR, US-035 cancelamento+reembolso individual, US-024 avaliacao, US-025 reputacao). CI local VERDE antes da revisao (reactor `verify` + frontend 97/97).

## Resumo
- Arquivos de producao revisados: 39 (backend ticket/event/payment + frontend).
- Achados: **P0 = 0 · P1 = 1 (aplicado) · P2 = 3 · P3 = 2**.
- **Veredicto: APROVADO COM RESSALVAS.** Nenhum bloqueador. As invariantes de dinheiro e de ingresso-usado-2x estao corretas (constraints + transicoes condicionais + lock pessimista, exatamente como os padroes S4/5A prescrevem). O unico P1 (log de perda silenciosa do gatilho de reembolso) foi **corrigido com teste**. As ressalvas P2/P3 sao follow-ups para o owner, nenhuma quebra invariante critica.

---

## Verificacao dos padroes S4/5A (confirmados APLICADOS — nao sao achados)
Antes de listar defeitos, confirmo que os padroes promovidos foram de fato aplicados aqui (o Revisor deve confirmar, nao presumir):

1. **Idempotencia AMQP (`processed_events` + ACK no-op) — OK.** `InscricaoCanceladaListener` (payment) faz `saveAndFlush(ProcessedEvent.of(eventId, ...))` na mesma tx do efeito; colisao de PK → `catch DataIntegrityViolationException` → `return` (ACK no-op). Nenhum caminho lanca em evento tardio → **sem poison message** (CR-S4-01 respeitado): pagamento ausente / PENDENTE / ja REEMBOLSADO → `reembolsarPorInscricao` retorna `false` sem lancar. Cobertura C1.a–C1.e (Testcontainers) confirma.
2. **afterCommit real — OK.** `CancelamentoInscricaoService.cancelar` registra `TransactionSynchronization.afterCommit` de dentro do `TransactionTemplate(REQUIRES_NEW)`; `liberarVaga` + `publicar` so rodam pos-commit. Rollback (ex.: 409 `INSCRICAO_JA_CANCELADA` via `setRollbackOnly`) → nada publica. Teste B4.a/B4.b (Testcontainers) trava isso. O `EventClient.getEvento` (I/O de rede) roda **antes** do `txTemplate.execute` → nenhuma conexao/lock de banco segurada durante HTTP (CR-S4-02 respeitado).
3. **Corrida reembolso individual vs. massa no mesmo pagamento — OK.** Ambos os caminhos usam `findByInscricaoIdForUpdate`/`findConfirmados...ForUpdate` (`PESSIMISTIC_WRITE`) + transicao `reembolsar()` condicional em `status='CONFIRMADO'`. O row lock do Postgres serializa → **exatamente 1 vence, o outro no-op**. As chaves de idempotencia NAO colidem (motivos distintos: `EVENTO_CANCELADO` vs `CANCELAMENTO_PARTICIPANTE`, `eventId` distinto por publisher) — a garantia de estorno unico vem da **transicao condicional sob lock**, nao da chave. Correto e intencional (ADR-T13/T15).
4. **Publisher com RabbitTemplate opcional — OK funcionalmente** (subir contexto H2 sem mockar), mas ver **CR-5B-01 (P1)** abaixo para o risco de observabilidade.
5. **Check-in duplo (US-034.6) — OK.** Barreira atomica = `UNIQUE(ingresso_id)` em `checkins` + `saveAndFlush(Checkin.de(...))`; colisao concorrente → `DataIntegrityViolationException` caught no `CheckinService` → 409 `INGRESSO_JA_UTILIZADO`. `Ingresso.realizarCheckin()` lanca 409 no caminho sequencial (distinto do `utilizar()` no-op da 5A). Correto.
6. **Token interno constante-no-tempo — OK.** `InternalTicketController.tokenValido` usa `MessageDigest.isEqual(...)` null-safe, nunca `String.equals` (CR-S3-05). Gateway nao roteia `/internal/**`. `TicketClient`/`TicketClientConfig` (event→ticket, outbound novo) espelha `EventClientConfig`: timeouts 2s/3s, `defaultHeader X-Internal-Token`, baseUrl de config. Token nunca logado (logs so status code). URL de config + path fixo → sem SSRF. Fail-closed (503 `TICKET_INDISPONIVEL`, nunca 500).
7. **Reputacao sem N+1 — OK.** `AvaliacaoRepository.agregarReputacao` = 1 query `SELECT new ReputacaoResponse(AVG(a.nota), COUNT(a)) ... WHERE eventoId=:id` (constructor projection, `idx_avaliacoes_evento`). Elegibilidade `participou` = 2 `EXISTS` indexados (`existsIngressoUtilizado...` + `existsByUsuarioIdAndEventoIdAndStatus`), sem varredura. O(1).
8. **Dinheiro — OK.** Reembolso individual reusa `Reembolso.criar(...,motivo)` (5A, `setScale(2)`) e `p.getValorBruto()`. `PagamentoResponse` mantem `@JsonFormat(STRING)` da 5A. Nenhum novo caminho de dinheiro fora do reuso.

---

## P1 — Importante (APLICADO com teste)

### CR-5B-01 — Perda silenciosa do gatilho de reembolso/saga quando RabbitTemplate ausente (log DEBUG → WARN)
- **Local:** `services/ticket-service/.../messaging/InscricaoCanceladaPublisher.java:36-39` e `PedidoCriadoPublisher.java:36-39` (ambos tocados nesta branch — o `PedidoCriadoPublisher` migrou para injecao opcional na Fase 5).
- **Porque:** o padrao "RabbitTemplate opcional (`@Autowired(required=false)`) + no-op se null" resolve corretamente o boot sob perfil `test` (H2, `RabbitAutoConfiguration` excluida). **Mas em producao o perfil NAO e `test`**, entao a autoconfig do broker esta ativa e o `RabbitTemplate` SEMPRE existe — um `null` aqui significa que **a autoconfig do broker falhou** (broker fora, credencial/URL errada). Nesse caso o publisher engolia a mensagem com apenas `log.debug(...)` — nivel tipicamente desligado em prod. Efeito: `inscricao.cancelada` nunca sai → **o reembolso do participante nunca e disparado, sem nenhum sinal visivel** (dinheiro que deveria voltar, some em silencio). Idem `pedido.criado` → a saga de pagamento inteira nao inicia. Isso e uma invariante de confianca (dinheiro), exatamente o que o Revisor prioriza.
- **Correcao aplicada:** elevado o log do ramo `rabbitTemplate == null` de `debug` para **`warn`**, com `inscricaoId`/`eventId` no texto, explicando que o gatilho nao sera disparado. **Sem mudanca de comportamento** (continua no-op para nao derrubar o cancelamento local, que ja commitou) — apenas torna a perda **observavel** na operacao. Uma alternativa mais agressiva (lancar/DLQ) foi rejeitada: derrubaria o cancelamento apos o commit local, criando inconsistencia pior; o correto e o cancelamento local commitar e a perda do reembolso gritar no log para reconciliacao manual (mesma filosofia do `[RECONCILIACAO]` em `liberarVagaComLog`).
- **Teste de regressao:** `services/ticket-service/.../messaging/InscricaoCanceladaPublisherTest.java` (NOVO, unit puro Mockito, roda local): (1) template presente → `convertAndSend(EXCHANGE, RK, evento)`; (2) template null → no-op, **nao lanca**; (3) `setRabbitTemplate(null)` explicito → no-op. Trava a invariante da injecao opcional.
- **Build:** `./mvnw -B -ntp -pl services/ticket-service -am test` → **BUILD SUCCESS, 104 testes, 0 falhas, 22 skipped** (Testcontainers gated por Docker). WARN observado disparando no log do teste.

---

## P2 — Ressalvas (devolvidas ao owner; NAO aplicadas)

### CR-5B-02 — Corrida check-in vs. cancelamento no mesmo ingresso: `ingressos.status` sem guard condicional (last-writer-wins)
- **Local:** `CheckinService.java:79-80` (`ingresso.realizarCheckin(); ingressoRepository.save(ingresso)`) e `CancelamentoInscricaoService.java:112-116` (`if (status==ATIVO) { ing.cancelar(); save(ing); }`).
- **Porque:** a `architecture.md` §Estrategias afirma que check-in e cancelamento simultaneos do mesmo ingresso sao serializados por "transicoes condicionais (`WHERE status='ATIVO'`) + row lock → exatamente um vence; o perdedor ve estado ja mudado → 409". **A implementacao NAO faz isso:** ambos os caminhos carregam o ingresso sem lock (`findByCodigoUnico` / `findByInscricaoId`) e persistem via dirty-check do JPA (`UPDATE ingressos SET status=? WHERE id=?`, **sem** `AND status='ATIVO'`). Resultado real: last-writer-wins. Se o cancel commitar depois do check-in, o ingresso termina `CANCELADO` embora exista um registro em `checkins` (ingresso usado marcado como cancelado); se o check-in commitar depois do cancel, termina `UTILIZADO` embora a inscricao esteja `CANCELADA` e a vaga tenha sido liberada. Nenhum 409 e emitido ao perdedor.
- **Avaliacao de severidade:** **P2, nao P0/P1.** Nao quebra invariante de dinheiro (reembolso continua unico — depende de `pagamentos.status`, nao de `ingressos.status`), nao permite check-in duplo (isso E guardado pelo `UNIQUE(ingresso_id)`), nao permite reembolso duplo. E um estado terminal inconsistente do ingresso numa corrida **entre atores distintos** (promotor na porta vs. participante cancelando ao mesmo tempo) — janela estreita e improvavel. O impacto pratico e cosmetico/auditoria.
- **Correcao proposta (owner decide):** trocar as duas mutacoes por `UPDATE` condicional atomico retornando `rowsAffected`, ex.: check-in `UPDATE ingressos SET status='UTILIZADO' WHERE id=:id AND status='ATIVO'` (0 → 409/404) e cancelamento `... SET status='CANCELADO' WHERE id=:id AND status='ATIVO'`. Assim o perdedor ve `rowsAffected=0` e o codigo pode reagir. **Nao apliquei** porque exige nova query no repo + decisao de contrato (o check-in perdedor vira 404? 409?) e um teste de concorrencia em Postgres real (nao verificavel local) — e o risco atual e baixo. Ou: **relaxar a afirmacao da `architecture.md`** para refletir o que o codigo garante (check-in duplo guardado por UNIQUE; corrida cross-actor com ingresso e last-writer-wins aceitavel). Recomendo alinhar doc↔codigo de qualquer forma.

### CR-5B-03 — `PagamentoService` construtor de compatibilidade deixa `reembolsoRepository` null
- **Local:** `services/payment-service/.../service/PagamentoService.java:68-74`.
- **Porque:** o construtor 5-arg (test-only, para `AfterCommitRollbackTest`) delega com `reembolsoRepository=null`. Se algum teste futuro instanciar por esse construtor e chamar `reembolsarPorInscricao`, dara NPE em `reembolsoRepository.save(...)` (linha 243). Em producao o bean usa o construtor 6-arg `@Autowired` → nao ha risco de runtime hoje.
- **Correcao proposta:** remover o construtor de compatibilidade e ajustar `AfterCommitRollbackTest` para passar um mock de `ReembolsoRepository` (ou `null` explicito com comentario), OU adicionar guard `Objects.requireNonNull` no metodo com mensagem clara. **P2** (divida de testabilidade, nao bug de producao). Nao aplicado — mexe em teste da 5A fora do escopo do diff 5B.

### CR-5B-04 — `CheckinService` depende do `GlobalExceptionHandler` generico de `DataIntegrityViolationException` se o catch local nao pegar
- **Local:** `CheckinService.java:85-90` (catch local → 409 `INGRESSO_JA_UTILIZADO`) vs. `ticket/exception/GlobalExceptionHandler.java:72-78` (handler generico → 409 `"JA_INSCRITO"`).
- **Porque:** o catch local traduz corretamente a colisao do `UNIQUE(ingresso_id)` para `INGRESSO_JA_UTILIZADO`. Mas o `saveAndFlush` esta dentro do try, entao a colisao e capturada localmente — OK. O risco e apenas se um refactor futuro mover o flush para fora do try: cairia no handler generico que responde `"JA_INSCRITO"` (mensagem errada para check-in). Hoje **nao e um bug** — o caminho esta correto. Registro como P2 de fragilidade: o handler generico com mensagem hardcoded `"JA_INSCRITO"` e um code-smell que ja atende mal a >1 constraint. **Correcao proposta:** o handler generico deveria devolver um code neutro (`CONFLITO_INTEGRIDADE`) e cada service traduzir o seu; ou mapear por constraint name. Nao aplicado (toca comportamento compartilhado, fora do minimo 5B).

---

## P3 — Menores (informativo)

### CR-5B-05 — Typo em identificador de variavel: `pagoDentroDoFrazo` / `dentroDoFrazo`
- **Local:** `CancelamentoInscricaoService.java:88,95` (`pagoDentroDoFrazo`) e teste `eventoPagoDentroDoFrazo`. Deveria ser `Prazo`. So legibilidade; sem efeito funcional. Renomear num toque futuro.

### CR-5B-06 — `Ingresso.realizarCheckin()` mapeia CANCELADO → 409 `INGRESSO_JA_UTILIZADO` (mensagem imprecisa)
- **Local:** `Ingresso.java:73-75`. Um ingresso CANCELADO retorna 409 `INGRESSO_JA_UTILIZADO` — mensagem semanticamente incorreta (nao foi utilizado, foi cancelado). Na pratica o `CheckinService` ja barra CANCELADO com 404 `INGRESSO_NAO_ENCONTRADO` **antes** de chamar `realizarCheckin` (linha 63-65), entao esse ramo e defesa-em-profundidade inalcancavel pelo fluxo normal. Aceitavel; se quiser precisao, usar um code distinto (`INGRESSO_CANCELADO`). Documentado no proprio codigo como "defesa".

---

## Recorrencias para promover a regra (→ coding-standards / decisions)

1. **[promover a coding-standards §Mensageria] Publisher com RabbitTemplate opcional: o no-op-quando-null deve logar em WARN, nao DEBUG.** O padrao "injecao opcional + no-op" (nascido para subir contexto H2 sem mockar) reapareceu 3× (`PagamentoAprovadoPublisher` S4, `PedidoCriadoPublisher` e `InscricaoCanceladaPublisher` 5B). Em producao um `RabbitTemplate` null = autoconfig do broker falhou = **evento de dominio perdido em silencio** (saga/reembolso nao disparam). Regra a promover: *"Publisher com RabbitTemplate opcional loga a perda em **WARN** (com a chave de negocio: inscricaoId/eventId), nunca DEBUG — a perda de um evento de dominio precisa ser observavel em prod."* Nota de follow-up: `PagamentoAprovadoPublisher` (payment, S4) ainda esta em DEBUG (`enviar()`); vale um toque de consistencia no proximo PR do payment — **nao alterado aqui por estar fora do diff 5B**.

2. **[promover a coding-standards §Concorrencia] Toda mutacao de `status` disputada por 2 caminhos concorrentes usa `UPDATE ... WHERE status=<esperado>` (rowsAffected), nao dirty-check do JPA.** O cancelamento voluntario ja faz isso corretamente (`cancelarPorParticipante`), mas o par check-in/cancel do MESMO ingresso caiu em dirty-check sem guard (CR-5B-02) — e a `architecture.md` afirmou uma serializacao que o codigo nao entrega. Regra: *"Quando a doc de arquitetura declara 'transicao condicional serializa a corrida', o Revisor confirma que a query e de fato `UPDATE...WHERE status=?` e nao um `save()` de entidade carregada sem lock."* (generaliza ADR-T07/T15).

3. **[manter] Doc↔codigo: afirmacoes de garantia de concorrencia na `architecture.md` devem casar 1:1 com a estrategia implementada.** CR-5B-02 e o segundo caso (apos S4) em que a doc promete mais serializacao do que o codigo entrega. Sugestao ao pipeline: o Tester/Revisor cruza cada linha da tabela §Estrategias com o metodo correspondente.

---

## Anexo — o que foi aplicado nesta revisao
- **Editado:** `InscricaoCanceladaPublisher.java`, `PedidoCriadoPublisher.java` (log DEBUG→WARN no ramo null, CR-5B-01). Sem mudanca de comportamento.
- **Criado:** `services/ticket-service/src/test/java/com/ticketeira/ticket/messaging/InscricaoCanceladaPublisherTest.java` (3 casos, unit).
- **Build de verificacao:** `./mvnw -B -ntp -pl services/ticket-service -am test` → **BUILD SUCCESS** (104 testes, 0 falhas, 22 skipped Testcontainers). Nao commitado (DevOps commita).
- **NAO tocado:** `docs/poster/`, `frontend/.gitignore`, nem qualquer P2/P3 (devolvidos ao owner).
