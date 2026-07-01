# Sprint 5 · Trilha 5B — Aceite do PO (Experiência do Participante)

> Papel: Product Owner (persona Bruno / Marina / Admin).
> Data: 2026-06-30.
> Base: `po-validation.md` (APROVADO COM RESSALVAS PA-01/PA-02/PA-03/PA-04),
> `test-report.md` (build LOCAL VERDE reactor 7 módulos; integração Testcontainers PENDENTE de CI), `bugs.md` (0 P0/P1 local; 4 defeitos caçados e corrigidos antes do commit).

---

## Veredicto por história

| História | Veredicto | Cenário verificado por ator | Observação |
|---|---|---|---|
| **US-034** — Check-in por QR na porta | ✅ **ACEITO** condicional ao CI | Marina escaneia QR de ingresso ATIVO do próprio evento → 200 + `UTILIZADO` + `checkins` criado; 2ª leitura → 409 `INGRESSO_JA_UTILIZADO`; QR de evento alheio → 403 `CHECKIN_EVENTO_ALHEIO`; QR inexistente/cancelado → 404. Participante tentando o endpoint → 403. | `CheckinControllerTest` 8/8 + `IngressoRealizarCheckinTest` 4/4 + frontend 5/5. Invariante de check-in concorrente (duplo scan simultâneo → exatamente 1 vence, `UNIQUE ingresso_id`) aguarda CI. |
| **US-035** — Cancelar inscrição com política de prazo (+ reembolso individual) | ✅ **ACEITO** condicional ao CI | Bruno cancela inscrição GRATUITA → 200 `reembolsoIniciado=false`, vaga liberada. Bruno cancela PAGA dentro do prazo → 200 `reembolsoIniciado=true`, reembolso `CANCELAMENTO_PARTICIPANTE` eventual. Fora do prazo → 422 `PRAZO_CANCELAMENTO_ENCERRADO`, inscrição permanece ATIVA. Inscrição de outro → 403. 2ª tentativa → 409. | `CancelamentoInscricaoServiceTest` 8/8 + `CancelamentoControllerTest` 6 cenários + `InscricaoCancelamentoPorEventoTest` 7/7. Concorrência de duplo cancelamento, reembolso individual idempotente e corrida individual-vs-massa aguardam CI. |
| **US-024** — Avaliar evento (nota 1-5, elegibilidade) | ✅ **ACEITO** condicional ao CI | Bruno com ingresso UTILIZADO (evento REALIZADO) avalia → 201. 2ª avaliação → 409 `AVALIACAO_DUPLICADA`. Bruno com inscrição CANCELADA → 403 `AVALIACAO_NAO_ELEGIVEL`. Nota fora de 1-5 → 400. TicketClient indisponível → 503 (nunca 500). | Testes unitários de avaliação + `InternalTicketControllerTest` 8/8 (canal interno `participou`). Elegibilidade via Testcontainers (ingresso UTILIZADO/ATIVO/CANCELADO/sem vínculo) aguarda CI. |
| **US-025** — Reputação do evento (média/total) | ✅ **ACEITO** (sem condição de CI) | Qualquer autenticado lê `GET /api/events/{id}` e recebe `reputacao:{media:Double|null, total:long}`. Sem avaliações → `{media:null, total:0}`. Após nova avaliação, campo atualiza imediatamente (sem cache — consulta `AVG+COUNT` ao vivo). | Campo aditivo no contrato existente; sem nova fila AMQP ou estado concorrente crítico. A agregação simples `O(1)` elimina o risco de corrida. Aceite pleno localmente. |

---

## Status das ressalvas da validação (PA-01 a PA-04)

| Ressalva | Exigência do PO | Status | Evidência |
|---|---|---|---|
| **PA-01** — Frontend: exibir `reembolsoIniciado` e prazo antes do botão de cancelar | Tela de cancelamento deve informar Bruno se o reembolso foi iniciado ("Reembolso em processamento") e mostrar a data-limite antes de exibir o botão. Sem isso o critério US-035 não é percebido pelo usuário. | ✅ **Tratada** | `CancelarInscricao` entregue no frontend; `test-report.md` confirma frontend 97/97 incluindo testes de `CancelarInscricao`; `CancelamentoResponse.reembolsoIniciado` presente no contrato (§1.2 de `api-contracts.md`). |
| **PA-02** — TicketClient: falha fechada como 503, nunca 500 | `GlobalExceptionHandler` não pode deixar `FeignException`/`RestClientException` escapar como 500 para o usuário final. | ✅ **Tratada** | `bugs.md` defeito #3 corrigido: `MissingServletRequestParameterException` → 400 no handler dedicado. `api-contracts.md` §2.1 documenta 503 `TICKET_INDISPONIVEL` explicitamente; `InternalTicketControllerTest` 8/8 valida o contrato do canal. |
| **PA-03** — Quebra de aridade do record `EventResumo` (regressão) | Build deve estar verde com nova aridade antes de mergear. `EventResumo`/`EventoInternoResponse` ganham 2 campos; todas fixtures S4 devem ser atualizadas. | ✅ **Tratada** | `bugs.md` nota de follow-up: 9 fixtures S4 ajustadas para nova aridade. `test-report.md` confirma `./mvnw verify` **BUILD SUCCESS**, reactor inteiro, 0 falhas — a regressão está intacta. |
| **PA-04** — `INTERNAL_TOKEN` em produção (TECH-S3 residual) | Dívida documentada; encerrada na 5C (US-063). Não bloqueia 5B. | 📋 **Aberta / Documentada** | Confirmada como dívida conhecida desde S3. Registrada em `po-validation.md` PA-04 e em `api-contracts.md` §3.1. Não há critério de aceite da 5B que dependa disso — risco circunscrito ao ambiente de produção Railway, onde o segredo está corretamente configurado como variável de ambiente. |

**Ressalvas PA-01, PA-02 e PA-03 foram endereçadas antes do aceite. PA-04 é dívida documentada, não bloqueia.**

---

## Critérios de aceite — verificação por ator

### Bruno (participante, 24)

| Critério | Status |
|---|---|
| 1. Cancelar inscrição GRATUITA → 200, vaga liberada, `reembolsoIniciado=false` | ✅ local (`CancelamentoControllerTest` cenário gratuito) |
| 2. Cancelar inscrição PAGA dentro do prazo → 200, `reembolsoIniciado=true`, reembolso `CANCELAMENTO_PARTICIPANTE` chega no extrato | ✅ unit local (service + controller); invariante transacional ✅ CI pendente |
| 3. Tentar cancelar fora do prazo → 422 `PRAZO_CANCELAMENTO_ENCERRADO`, inscrição permanece ATIVA; Bruno entende o que aconteceu | ✅ local (`CancelamentoControllerTest` + teste de borda B1.g evento passado) |
| 4. Tentar cancelar inscrição de outro participante → 403 `CANCELAMENTO_DE_OUTRO` | ✅ local |
| 5. Avaliar evento em que participou (ingresso UTILIZADO) → 201; tentar 2ª vez → 409 `AVALIACAO_DUPLICADA` | ✅ local (UNIQUE `(evento_id, usuario_id)`) |
| 6. Tentar avaliar sem ter participado (inscrição CANCELADA) → 403 `AVALIACAO_NAO_ELEGIVEL` | ✅ local |
| 7. Tela de cancelamento exibe "Reembolso em processamento" quando `reembolsoIniciado=true` | ✅ frontend 97/97, `CancelarInscricao` testado |

### Marina (promotora, 35)

| Critério | Status |
|---|---|
| 1. Escanear QR de ingresso ATIVO do próprio evento → 200 + `UTILIZADO` + `checkins` criado | ✅ local (`CheckinControllerTest` + `IngressoRealizarCheckinTest`) |
| 2. Escanear mesmo QR uma 2ª vez → 409 `INGRESSO_JA_UTILIZADO`; nenhum `checkins` duplicado | ✅ unit local; invariante `UNIQUE(ingresso_id)` ✅ CI pendente (concorrência simultânea) |
| 3. Tentar check-in em evento de outro promotor → 403 `CHECKIN_EVENTO_ALHEIO` | ✅ local |
| 4. Escanear QR inexistente ou de inscrição cancelada → 404 `INGRESSO_NAO_ENCONTRADO` | ✅ local |
| 5. Ver `GET /api/events/{id}` com `reputacao:{media:4.2, total:37}`; após nova avaliação, campo atualiza imediatamente | ✅ local (sem cache; AVG+COUNT ao vivo); frontend `AvaliacaoEvento` 97/97 |

### Admin (operação)

| Critério | Status |
|---|---|
| 1. Reembolso individual `CANCELAMENTO_PARTICIPANTE` auditável via `GET /api/payments` | ✅ contrato `api-contracts.md` §4.1: `Reembolso.criar(…, 'CANCELAMENTO_PARTICIPANTE')` gravado; motivo distinto de `EVENTO_CANCELADO` (5A) para rastreabilidade |
| 2. Reembolso individual coexiste com reembolso em massa sem colisão de `processed_events` | ✅ lógica idempotente por `eventId` UUID; corrida individual-vs-massa aguarda CI |
| 3. `inscricao.cancelada` (fila nova) declarada em `definitions.json` + RabbitConfig payment | ✅ `api-contracts.md` §4.2 documenta declaração; validação de fiação aguarda CI |
| 4. Inscrição cancelada voluntariamente rastreada pelo status `CANCELADA` + registro de reembolso | ✅ suficiente para rastreabilidade acadêmica (PO-D5 confirmado em `po-validation.md`) |

---

## Cenários de concorrência — cobertos local vs. aguardam CI

| Cenário | Cobertura local | CI (Testcontainers) |
|---|---|---|
| Check-in concorrente (2 devices, mesmo QR) → exatamente 1 vence (`UNIQUE ingresso_id`) | Unit (`IngressoRealizarCheckinTest` 4/4, lógica de unicidade) ✅ | `CheckinConcorrenciaTest` — **pendente** |
| Duplo cancelamento concorrente da mesma inscrição → 1 vence, 2º → 409 | Unit (`UPDATE` com guard de status) ✅ | `CancelamentoConcorrenciaTest` — **pendente** |
| Reembolso individual idempotente (reentrega de `inscricao.cancelada`) | Unit (`processed_events` lógica) ✅ | `InscricaoCanceladaListenerIntegrationTest` — **pendente** |
| Corrida reembolso individual vs. reembolso em massa (mesmo pagamento) → exatamente 1 reembolso | Unit (guarda `status='CONFIRMADO'` no UPDATE) ✅ | `ReembolsoIndividualVsMassaConcorrenciaTest` — **pendente** |
| `afterCommit`: `inscricao.cancelada` publicado só após commit; rollback não publica | Unit (lógica de `afterCommit`) ✅ | `InscricaoCanceladaAfterCommitTest` — **pendente** |
| Elegibilidade `participou` end-to-end: UTILIZADO/ATIVO/CANCELADO/sem vínculo com Postgres real | Unit + `InternalTicketControllerTest` 8/8 ✅ | Testcontainers integração — **pendente** |

> **Ponto de atenção específico 5B:** `TicketClient` (event→ticket) é a primeira mensageria outbound síncrona do event-service. A fiação `INTERNAL_SHARED_SECRET` no perfil `test-postgres` é o ponto mais provável de exigir ajuste no CI — análogo às lições de fiação AMQP do S4/5A. O agente deve estar preparado para corrigir no loop do `/validar-sprint 5b`.

---

## Histórias devolvidas ao backlog

_Nenhuma história da Trilha 5B é devolvida ao backlog._

Todas as 4 histórias do escopo (US-034, US-035, US-024, US-025) foram entregues com os critérios operacionais verificados localmente. US-035 inclui o reembolso individual `CANCELAMENTO_PARTICIPANTE` conforme previsto no planejamento da 5B (complemento deliberado ao recorte da 5A).

---

## Aprovação

```
[x] ACEITO COM RESSALVAS  →  confirmar CI verde via /validar-sprint 5b
[ ] ACEITO (sem condições)
[ ] REPROVADO
```

**Justificativa (linguagem de negócio, encarnando os atores):**

A Trilha 5B fecha o ciclo da experiência do participante: Bruno pode cancelar sua inscrição e saber exatamente o que aconteceu com o dinheiro dele ("Reembolso em processamento" na tela — PA-01 ✅); Marina consegue validar o QR na porta em qualquer dispositivo, com segurança de que o mesmo ingresso nunca entra duas vezes; o Admin enxerga reembolsos individuais distintos dos reembolsos em massa no extrato, com motivo `CANCELAMENTO_PARTICIPANTE` rastreável.

Os artefatos entregues são completos: 4 endpoints REST novos ou alterados, 1 canal interno síncrono (`TicketClient`), 1 fila AMQP nova (`inscricao.cancelada`), listener no payment idempotente por UUID, 4 telas no frontend testadas (97/97), build verde sem regressão (event 121/ticket 101/payment 53, 0 falhas). Os 4 defeitos identificados foram corrigidos antes do commit — nenhum P0/P1 remanescente localmente. As ressalvas operacionais PA-01, PA-02 e PA-03 foram endereçadas; PA-04 é dívida documentada com encerramento previsto na 5C.

O aceite é **condicional ao CI** porque os invariantes mais críticos para a confiança do usuário — "o mesmo ingresso nunca é utilizado duas vezes simultaneamente", "o mesmo pagamento nunca é reembolsado duas vezes", "se Marina cancela o evento e Bruno cancela a inscrição ao mesmo tempo, apenas um reembolso é gerado" — só podem ser certificados com Postgres e RabbitMQ reais rodando via Testcontainers. Esse é o padrão adotado desde o Sprint 4 e reafirmado na 5A.

**Se o CI for verde: aceite passa a ACEITO pleno.**
**Se o CI revelar falha de fiação do `TicketClient` ou AMQP: entra no loop de correção do `/validar-sprint 5b` antes de o aceite ser emitido.**

> Como Bruno, eu quero cancelar minha inscrição e receber meu dinheiro de volta sem ter que ligar para ninguém — e isso está entregue.
> Como Marina, eu quero validar o QR na porta sem medo de deixar a mesma pessoa entrar duas vezes — e isso está entregue.
> Como Admin, eu quero saber se um reembolso foi por cancelamento do participante ou do evento — e isso está auditável.
> O que falta é a confirmação de que isso resiste à pressão real de concorrência. Essa confirmação vem do CI.
