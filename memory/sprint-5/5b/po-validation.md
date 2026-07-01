# Sprint 5B — Validacao PO da Arquitetura (Trilha Experiencia)

> Fase 3 do pipeline SDD. PO valida cobertura de historia e decisoes de produto.
> **Nao valida design tecnico** (responsabilidade do Arquiteto — ADR-T14/T15/T16 ja registrados).
> Data: 2026-06-30.

---

## Historias cobertas

| Historia | Veredicto | Observacao |
|---|---|---|
| US-034 — Check-in por QR | ✅ COBERTA | Todos os cenarios do criterio de aceite mapeados em testes (A2.a–h, A3.a). |
| US-035 — Cancelar inscricao com politica | ✅ COBERTA | Gratuito, pago dentro/fora do prazo, alheia, concorrencia — todos representados. |
| US-024 — Avaliar evento (elegibilidade) | ✅ COBERTA | Elegivel 201, 2a 409, nao-elegivel 403, nota 400, concorrencia via UNIQUE. |
| US-025 — Reputacao no detalhe do evento | ✅ COBERTA | Media+total, sem cache, qualquer autenticado le, null/0 sem avaliacoes. |

---

## Decisoes confirmadas pelo PO (5 itens)

### D1 (Pergunta 1) — Reembolso individual ASSÍNCRONO via `inscricao.cancelada`

**CONFIRMADO.**

O reembolso individual segue o mesmo modelo eventual da 5A (`evento.cancelado`): o cancelamento commita localmente (inscricao/ingresso/vaga), e o reembolso chega "em instantes" quando o payment consumir a fila. O criterio US-035.2 diz "Bruno ve o reembolso no extrato" — nao exige que seja no mesmo request. A consistencia eventual e aceitavel e o campo `reembolsoIniciado=true` na resposta do DELETE sinaliza ao frontend que o processo foi iniciado, evitando confusao.

**Ressalva operacional registrada:** o frontend **deve** exibir esse feedback ("Reembolso em processamento") para que Bruno nao fique sem saber o que aconteceu. O Back/Front tratam via campo `reembolsoIniciado` na `CancelamentoResponse`. Se esse campo sumir da resposta, o criterio de aceite nao e satisfeito — o Tester verifica.

### D2 (Pergunta 2) — Formula do prazo: `now <= data_inicio - prazo_reembolso_dias`; fora → 422 bloqueia

**CONFIRMADO.**

A formula `now <= data_inicio - prazo_reembolso_dias` e clara e operacionalmente honesta. Bloquear com 422 (em vez de cancelar sem reembolso silenciosamente) e a decisao correta de produto: Bruno recebe uma mensagem explicativa e sabe exatamente o que aconteceu. A borda `data_inicio ja passou → fora do prazo` esta coberta (teste B1.g). A formula inclui o limite exato (`<=`), ou seja, no dia exato do limite Bruno ainda pode cancelar — comportamento esperado e positivo.

**Confirmacao adicional:** para eventos **GRATUITOS** nao ha prazo (sempre pode cancelar, sem reembolso). Isso esta consistente com o criterio US-035.1 e com o codigo que so aplica a checagem para `tipo == PAGO`.

### D3 (Pergunta 3) — Elegibilidade: evento REALIZADO (event) AND (ingresso UTILIZADO OU inscricao ATIVA) (ticket)

**CONFIRMADO.**

A divisao de responsabilidade e correta: o event pre-filtra `evento.status == REALIZADO` (barato, local) antes de consultar o ticket, e o ticket responde `{participou: boolean}` baseado em `ingresso UTILIZADO OU inscricao ATIVA`. Ingresso UTILIZADO **so habilita** avaliacao apos o promotor encerrar o evento (o pre-filtro REALIZADO impede avaliacao prematura) — este e o comportamento de produto desejado.

**Confirmacao da regra PO-D1 original:** inscricao CANCELADA nao conta (o ticket retorna `false` para ela). Um participante que cancelou nao pode avaliar — coerente com a justificativa do planejamento (nao distorcer a reputacao com quem nao foi ao evento).

### D4 (Pergunta 4) — Reputacao = `{media, total}` sem cache, recalculada por agregacao

**CONFIRMADO para o MVP.**

Uma unica query `AVG + COUNT` indexada por `evento_id` (V1) e O(1) — nao ha N+1 nem risco de performance para o volume academico do projeto. O criterio US-025.2 ("sem cache obsoleto") e satisfeito pela ausencia de cache. O front recebe `media: Double | null` e formata as casas decimais — responsabilidade correta de cada camada.

**Nota de produto:** se o volume de avaliacoes crescer muito (cenario pos-MVP), adicionar um campo desnormalizado `reputacao_media` na tabela `eventos` e a evolucao natural — mas nao e necessario agora e nao deve entrar na 5B sem ADR proprio.

### D5 (Pergunta 5) — Zero migrations Flyway + auditoria de cancelamento voluntario

**CONFIRMADO com ressalva.**

O schema existente (V1/V2) cobre todos os dados necessarios para a 5B: `checkins`, `avaliacoes`, `reembolsos.motivo CHECK('CANCELAMENTO_PARTICIPANTE')`, `processed_events`. Nenhuma coluna nova de produto esta faltando para os criterios de aceite das 4 historias.

**Decisao sobre auditoria de cancelamento:** o time de Arquitetura pergunta se e necessario registrar `cancelada_em` / `motivo` em `inscricoes` para auditoria. **O PO decide: NAO entra na 5B.** O status `CANCELADA` + o registro `reembolsos(CANCELAMENTO_PARTICIPANTE)` (com timestamp `reembolsado_em`) + o log de transacao **ja sao suficientes** para rastreabilidade academica. Uma migration `V3__cancelamento_audit.sql` seria scope creep neste momento — se o dono pedir explicitamente, entra com ADR na 5C ou em sprint posterior.

---

## Aderencia ao escopo

- [x] Nenhuma feature fora do roadmap (RF07 cancelamento, RF08 avaliacao/reputacao, RF10 check-in).
- [x] Check-in (US-034): Marina valida QR do proprio evento -> 200 + UTILIZADO; 2o -> 409; alheio -> 403/404. **Coberto.**
- [x] Cancelamento (US-035): gratuito libera vaga; pago dentro do prazo -> reembolso eventual; fora do prazo -> 422 bloqueia. **Coberto.**
- [x] Avaliacao (US-024): elegivel avalia 1x (409 na 2a); nao-elegivel 403; detalhe mostra media + total. **Coberto.**
- [x] Reputacao (US-025): `GET /api/events/{id}` inclui `reputacao{media, total}`; atualiza imediatamente; qualquer autenticado le. **Coberto.**
- [x] Cenarios de concorrencia mapeados (duplo check-in, duplo cancelamento, dupla avaliacao, corrida individual-vs-massa).
- [x] Regressao da 5A documentada e testada (F1/F2/F3 na spec de testes).

---

## Pontos de atencao (nao bloqueiam, mas exigem verificacao na entrega)

### PA-01 — Frontend: exibir `reembolsoIniciado` e prazo de cancelamento (R5 do planning)

O campo `reembolsoIniciado: true/false` na `CancelamentoResponse` e o meio pelo qual o frontend sabe se o reembolso foi iniciado. Se o frontend nao exibir esse feedback ("Reembolso em processamento"), Bruno fica sem informacao. Alem disso, o frontend **deve** exibir a data-limite de cancelamento antes do botao — isso e o criterio implícito do R5 do planning do PO. O Tester valida no aceite final (po-acceptance.md) que a tela de cancelamento informa o prazo.

### PA-02 — TicketClient: falha fechada como 503, nunca 500 (US-024 resilencia)

A primeira chamada outbound do event-service (event->ticket via `TicketClient`) deve tratar erros internos como 503 `TICKET_INDISPONIVEL`, nunca propagar 500. O test D4.b cobre isso. O Revisor confirma que o `GlobalExceptionHandler` nao deixa escapar `FeignException`/`RestClientException` como 500.

### PA-03 — Quebra de aridade do record `EventResumo` (regressao)

A adicao de `dataInicio` e `prazoReembolsoDias` ao record `EventResumo` (ticket) e `EventoInternoResponse` (event) quebra a aridade dos construtores. **Todos** os testes existentes que instanciam esses records precisam ser atualizados (F1 da spec de testes). O Tester/Back confirmam que o build (`./mvnw verify`) esta verde antes de mergear a 5B.

### PA-04 — `INTERNAL_TOKEN` em producao (TECH-S3 residual)

O `TicketClient` outbound (novo canal event->ticket) reusa o `INTERNAL_SHARED_SECRET`. Se o token estiver como `dev-internal-secret` em producao, o US-024 e inseguro. Esse risco ja estava registrado como TECH-S3-04 e sera encerrado na 5C (US-063). **Nao e bloqueio da 5B** — e dívida documentada.

---

## Cenarios de ator (verificacao humana, encarnando os atores)

### Bruno (participante, 24)

1. **Check-in indevido:** Bruno tenta acessar `POST /api/tickets/checkin` com papel `PARTICIPANTE` (nao tem) → 403. Bruno nunca consegue fazer check-in no lugar de Marina. ✅
2. **Cancelamento dentro do prazo (pago):** Bruno cancela inscricao em evento pago, 3 dias antes do inicio (prazo = 7 dias) → 200, `reembolsoIniciado=true`, extrato mostra `REEMBOLSADO` em instantes. ✅
3. **Cancelamento fora do prazo:** Bruno tenta cancelar inscricao paga 1 dia antes do inicio (prazo = 7 dias) → 422 com mensagem "Prazo de cancelamento encerrado." → Bruno entende o que aconteceu, inscricao permanece ATIVA. ✅
4. **Cancelar inscricao de outro:** Bruno nao pode cancelar a inscricao de outro participante → 403. ✅
5. **Avaliar evento que participou:** Bruno faz avaliacao de evento REALIZADO onde tinha ingresso UTILIZADO → 201. Tenta avaliar uma segunda vez → 409. ✅
6. **Avaliar sem ter participado:** Bruno (inscricao CANCELADA) tenta avaliar → 403 `AVALIACAO_NAO_ELEGIVEL`. ✅

### Marina (promotora, 35)

1. **Check-in valido:** Marina escaneia QR de ingresso ATIVO do proprio evento → 200, ingresso vira `UTILIZADO`, `checkins` criado. ✅
2. **Duplo check-in:** Marina (ou outro dispositivo) tenta escanear o mesmo QR → 409 `INGRESSO_JA_UTILIZADO`. Nunca duplica o `checkins`. ✅
3. **Check-in de evento alheio:** Marina tenta validar QR de evento de outro promotor → 403 `CHECKIN_EVENTO_ALHEIO`. ✅
4. **QR invalido:** Marina escaneia codigo que nao existe ou pertence a inscricao cancelada → 404. ✅
5. **Ver reputacao:** Marina abre `GET /api/events/{id}` do proprio evento e ve `reputacao:{media:4.2, total:37}`. Apos nova avaliacao, o valor atualiza imediatamente (sem cache). ✅

---

## Aprovacao

[x] **APROVADO COM RESSALVAS**

As ressalvas sao operacionais (nao bloqueiam o desenvolvimento), todas rastreadas:

1. **PA-01 (frontend obrigatorio):** o frontend deve exibir `reembolsoIniciado` e a data-limite do prazo antes do botao de cancelar. Sem isso, o criterio de produto do US-035 nao e percebido pelo usuario. **Verificar no po-acceptance.md.**
2. **PA-03 (regressao do record):** garantir que o build esta verde com a nova aridade do `EventResumo`/`EventoInternoResponse` antes de mergear. **Gate de CI obrigatorio.**
3. **PA-02 (falha fechada TicketClient):** o Revisor confirma no code review que nenhum erro do canal interno vira 500 para o usuario final.
4. **PA-04 (INTERNAL_TOKEN):** divida documentada, encerrada na 5C — nao bloqueia 5B.

**Nenhuma historia esta bloqueada. O time pode iniciar o desenvolvimento da trilha 5B.**
