# Sprint 5 — Spec mestre (ultra-plan) · "Sprint de fechamento"

> Orquestrador (ultra-plan). Fonte para PO, Arquiteto, Back, Front, Tester.
> Pedido do dono: "toda a saga restante" — US-042, US-043, US-024, US-025, US-034, US-035, US-061 (+ "adicione o que faltar").
> **US-060 NÃO entra aqui** — já está no Sprint 4 (fundação AMQP, ADR-P10). O S5 **reusa** o padrão `processed_events`/`afterCommit` para o consumidor `evento.finalizado`.

## 0. Aviso de tamanho (leia antes de aprovar)
Este é um **sprint de fechamento** que completa o ciclo de vida inteiro do produto: financeiro (reembolso/repasse), experiência (check-in, cancelamento, avaliações/reputação) e qualidade (carga + observabilidade). São **7 histórias em 4 serviços** (≈2× um sprint normal de ~2 semanas). **Recomendação:** fasear em **5A → 5B → 5C** (§13) e aprovar trilha a trilha. Se preferir um único bloco, ok — mas o risco de não fechar em 2 semanas é real e está registrado (§9 R0).

## 1. Objetivo (1 frase)
Ao fim deste sprint, **o ciclo de vida completo fecha**: Marina valida ingresso por QR na porta, Bruno cancela/avalia, o dinheiro em escrow é **repassado** (−10%) ao promotor após o evento realizado ou **reembolsado** se cancelado — e o abre-vendas é validado sob carga.

## 2. Escopo por trilha

### Trilha 5A — Financeiro / fim da saga (RF06)
| ID | História | Requisito |
|---|---|---|
| US-043 | Como promotor, quero receber o **repasse** (−10% taxa) após o evento **REALIZADO** | RF06 |
| US-042 | Como participante, quero ser **reembolsado** se o evento for cancelado (ou se eu cancelar dentro da política) | RF06 |
| *(técnico, embutido)* | Ligar a 3ª fila AMQP **`evento.finalizado`** + introduzir **`evento.cancelado`**: transições de status do evento viram gatilhos de repasse/reembolso | ADR-T04 |

### Trilha 5B — Experiência / ciclo de vida do ingresso (RF07, RF08, RF10)
| ID | História | Requisito |
|---|---|---|
| US-034 | Como promotor, quero **validar o ingresso (check-in por QR)** na porta | RF10 |
| US-035 | Como participante, quero **cancelar minha inscrição** conforme a política (libera vaga; se pago, dispara reembolso) | RF07 |
| US-024 | Como participante, quero **avaliar** um evento que participei (nota 1-5 + comentário) | RF08 |
| US-025 | Como promotor, quero ver a **reputação** (média de avaliações) do meu evento | RF08 |

### Trilha 5C — Qualidade (RNF09)
| ID | História | Requisito |
|---|---|---|
| US-061 | Como time, quero **testes de carga** no abre-vendas (concorrência de inscrição) | RNF09 |
| **US-062** *(proposto)* | Observabilidade básica: Actuator health/readiness + métricas + logs estruturados | RNF09 |
| **US-063** *(proposto)* | Hardening pré-banca: fechar dívidas ADR-T03 (whitelist do gateway → match exato), ADR-T05 (seed admin env-driven) e follow-ups TECH-S3-01..04 | — |

> **US-062 e US-063 são adições minhas** ("pode adicionar o que faltar"). Dono confirma/corta no gate.

## 3. Serviços afetados + delta de modelo de dados (Flyway)

| Serviço | Mudança | Migration |
|---|---|---|
| **event-service** | Transições de status: `PUBLICADO → REALIZADO` (gatilho repasse) e `PUBLICADO → CANCELADO` (gatilho reembolso). Produz `evento.finalizado` e `evento.cancelado`. Avaliações (US-024) + reputação/média (US-025). Expõe `promotorId` no resumo interno (carga do S4). | Possível **V3**: índice/coluna p/ controle de finalização (ex.: `finalizado_em`) se a finalização for por job. avaliacoes já em V1. |
| **payment-service** | Consome `evento.finalizado` → **repasse** (`CONFIRMADO → REPASSADO`, libera escrow −10%). Consome `evento.cancelado` → **reembolso** em massa (`CONFIRMADO → REEMBOLSADO` + `reembolsos`). Reembolso individual por cancelamento de inscrição. | reembolsos já em V1; `processed_events` já no S4. Sem migration nova provável. |
| **ticket-service** | **Check-in** (US-034): lookup `codigo_unico` → `ingressos.status=UTILIZADO` + `checkins` (UNIQUE). **Cancelamento** (US-035): `inscricoes.status=CANCELADA` + `ingressos.status=CANCELADO` + libera vaga (ADR-T07) + dispara reembolso se pago. | checkins/status já em V1; PENDENTE_PAGAMENTO no S4. Sem migration nova provável. |
| **frontend** | Telas: scanner/validação de QR (Marina), cancelar inscrição (Bruno), avaliar evento + ver reputação, status de reembolso/repasse. | — |
| **infra** | `evento.finalizado` já declarada em `definitions.json`. **`evento.cancelado`** é nova → adicionar fila + binding (+ DLQ). | `definitions.json` |

## 4. Endpoints + eventos AMQP previstos

**REST (gateway):**
- `POST /api/ingressos/checkin` (ticket) — body `{codigo_unico}`; **só promotor dono do evento**; → 200 (check-in ok) / 409 (já utilizado) / 404 / 403. (US-034)
- `DELETE /api/inscricoes/{id}` (ticket) — cancela inscrição própria dentro da política; libera vaga; se pago → dispara reembolso. (US-035)
- `POST /api/eventos/{id}/avaliacoes` (event) — `{nota, comentario}`; só quem participou; UNIQUE evento+usuario. (US-024)
- `GET /api/eventos/{id}` (event) — passa a incluir **reputação** (média + total). (US-025)
- `POST /api/eventos/{id}/cancelar` (event) — promotor dono cancela → publica `evento.cancelado`. (gatilho US-042)
- `POST /api/eventos/{id}/encerrar` **ou** job agendado (event) — marca REALIZADO → publica `evento.finalizado`. (gatilho US-043) *(Arquiteto decide endpoint vs. job)*
- `GET /api/payments/me` — passa a refletir REEMBOLSADO/REPASSADO. (US-042/043)

**AMQP (exchange `ticketeira.events`, topic):**
| Routing key | Produtor | Consumidor | Efeito |
|---|---|---|---|
| `evento.finalizado` | event | payment | repasse: `pagamentos CONFIRMADO → REPASSADO` (−10% já computado no S4) |
| `evento.cancelado` *(nova fila)* | event | payment **e** ticket | payment: reembolso em massa; ticket: cancela inscrições + libera/zera vagas |
> Idempotência: `processed_events` (padrão US-060/S4) em todo novo consumidor; publish em `afterCommit`.

## 5. Fluxos / sagas

```
REPASSE (US-043):
  Evento PUBLICADO → REALIZADO (job/endpoint) → publica evento.finalizado
  payment consome (idempotente) → para cada pagamento CONFIRMADO do evento:
     status → REPASSADO (escrow liberado; valor_repasse = bruto − 10%)

REEMBOLSO por cancelamento do evento (US-042):
  Promotor cancela evento → status CANCELADO → publica evento.cancelado
  payment consome → pagamentos CONFIRMADO → REEMBOLSADO + cria reembolsos(EVENTO_CANCELADO)
  ticket consome → inscricoes ATIVA → CANCELADA + ingressos → CANCELADO (libera vagas)

CANCELAMENTO pelo participante (US-035) [+ reembolso individual US-042]:
  Bruno DELETE inscrição (dentro de prazo_reembolso_dias) →
     ticket: inscricao CANCELADA + ingresso CANCELADO + liberarVaga (ADR-T07)
     se PAGO: dispara reembolso individual → payment: pagamento → REEMBOLSADO + reembolsos(CANCELAMENTO_PARTICIPANTE)

CHECK-IN (US-034):
  Marina (promotor dono) POST checkin {codigo_unico} →
     lookup ingresso ATIVO do evento dela → status UTILIZADO + checkins (UNIQUE ingresso_id)
     2º check-in do mesmo QR → 409

AVALIAÇÃO (US-024/025):
  Bruno avalia evento REALIZADO em que participou (regra de elegibilidade §7) → avaliacoes (UNIQUE)
  Reputação = AVG(nota) + total, exposta no detalhe do evento / visão do promotor
```

## 6. Regras de produto a decidir (PO/Arquiteto)
- **Elegibilidade de avaliação (US-024):** candidato = quem tem **ingresso UTILIZADO** (fez check-in) **ou** inscrição ATIVA em evento **REALIZADO**. *(PO decide.)*
- **Finalização do evento (US-043):** **job agendado** (`data_fim < now → REALIZADO`) **ou** ação do promotor "encerrar". *(Arquiteto decide; afeta avaliação e repasse.)*
- **Política de reembolso (US-035):** janela = `prazo_reembolso_dias` do evento; fora do prazo → cancela sem reembolso (ou bloqueia). *(PO decide.)*

## 7. Pontos de concorrência + estratégia
| Cenário | Estratégia |
|---|---|
| Duplo check-in do mesmo ingresso | `UNIQUE(ingresso_id)` em `checkins` + transição `ATIVO→UTILIZADO` condicional → 1 sucesso, 2º = 409 |
| Cancelar e fazer check-in ao mesmo tempo | transição de status condicional (UPDATE ... WHERE status='ATIVO') serializa |
| `evento.finalizado`/`evento.cancelado` reentregue | `processed_events` (S4) → repasse/reembolso aplicados 1× |
| Repasse e reembolso no mesmo pagamento (corrida) | transição condicional `WHERE status='CONFIRMADO'` (só um vence) |
| Liberar vaga no cancelamento | incremento limitado pela capacidade (ADR-T07, `WHERE vagas < capacidade`) |
| Avaliação dupla | `UNIQUE(evento_id, usuario_id)` → 409 |
| **Carga (US-061)** | abre-vendas: N usuários disputam M vagas → exatamente M sucessos, N−M × 409, vagas=0, **0 overbooking** (mede latência/throughput) |

## 8. Dependências
- **Depende do Sprint 4** (escrow/pagamento CONFIRMADO + AMQP idempotente ligados). Se o S4 não estiver mergeado, 5A não roda.
- US-043 → precisa de `evento.finalizado` + transição REALIZADO. US-042 → precisa de `evento.cancelado` + cancelamento. US-035 (pago) → dispara US-042. US-034 → precisa do `promotorId` exposto (S4) + papel PROMOTOR. US-024 → precisa de evento REALIZADO + check-in/inscrição. US-061 → precisa do fluxo de inscrição estável.

## 9. Riscos
- **R0 — Tamanho (≈2× sprint):** alto risco de não fechar em 2 semanas. Mitigação: **fasear 5A/5B/5C** (§13) e aprovar por trilha.
- **R1 — Transição de status do evento (REALIZADO/CANCELADO):** quem dispara? Job vs. ação do promotor muda o design. Decidir cedo (Arquiteto).
- **R2 — Reembolso em massa (evento cancelado):** consistência cross-service (payment + ticket consomem o mesmo `evento.cancelado`) — idempotência e ordenação. Constraints + `processed_events` como rede.
- **R3 — Carga (US-061):** escolher ferramenta (k6/Gatling/JMeter) + ambiente (Postcres+Rabbit reais via Testcontainers ou stack Docker). Não rodar no runner padrão do CI sem cuidado (tempo).
- **R4 — Autorização do check-in:** promotor só valida ingresso do **próprio** evento (ownership), não só papel PROMOTOR.

## 10. Dívidas tocadas/fechadas
- **ADR-T04 (consumidores RabbitMQ):** terceira fila `evento.finalizado` + nova `evento.cancelado` ligadas → dívida efetivamente encerrada.
- **(US-063, se aprovado)** ADR-T03 (whitelist gateway → match exato), ADR-T05 (seed admin env-driven), TECH-S3-01/02/03/04.

## 11. Critérios de sucesso verificáveis
1. **Repasse:** evento PAGO realizado → `pagamentos.status=REPASSADO`, `valor_repasse = bruto − 10%`; promotor vê o repasse. Reentrega de `evento.finalizado` → repasse 1× só.
2. **Reembolso:** promotor cancela evento PAGO → todos os pagamentos `REEMBOLSADO` + inscrições/ingressos CANCELADO + vagas liberadas; participante vê o reembolso.
3. **Cancelamento participante:** Bruno cancela dentro do prazo → vaga liberada; se pago, reembolso individual; fora do prazo → bloqueado/sem reembolso (regra §6).
4. **Check-in:** Marina valida QR válido → 200 + ingresso UTILIZADO; 2ª leitura → 409; QR de outro evento/dela-não → 403/404.
5. **Avaliação/reputação:** quem participou avalia (1×); detalhe do evento mostra média + total; não-participante é bloqueado.
6. **Carga:** N×M no abre-vendas → exatamente M ingressos, 0 overbooking, vagas=0; relatório de latência/throughput.
7. CI verde (back + front); Swagger atualizado; (se US-062) `/actuator/health` responde.

## 12. Fora deste sprint (intencional)
- US-060 (já no S4). Gateway de pagamento real; mTLS (ADR-T08). Reputação de **promotor** agregada entre eventos (só por evento aqui). Notificações por e-mail de repasse/reembolso (reusar infra de e-mail do user-service é possível, mas fica fora salvo se o dono pedir).

## 13. Recomendação de faseamento (decisão do dono no gate)
- **5A (financeiro):** US-043 + US-042 + wiring `evento.finalizado`/`evento.cancelado` + transições de status. *(fecha a saga financeira)*
- **5B (experiência):** US-034 + US-035 + US-024 + US-025. *(fecha o ciclo do participante/promotor)*
- **5C (qualidade):** US-061 + (US-062 observabilidade) + (US-063 hardening). *(fecha qualidade pré-banca)*
> Aprovar como 3 sprints curtos (5A/5B/5C) **ou** 1 mega-sprint — escolha do dono.

## 14. ADRs previstos (Arquiteto, em desenvolver-sprint)
- **ADR-Txx — Transição de status do evento** (REALIZADO/CANCELADO: job vs. endpoint) e os eventos `evento.finalizado`/`evento.cancelado`.
- **ADR-Txx — Saga de repasse e reembolso** (escrow → REPASSADO/REEMBOLSADO; reembolso em massa vs. individual; idempotência).
- **ADR-Txx — Autorização do check-in** (papel PROMOTOR + ownership do evento).
- **ADR-P11 — Escopo do Sprint 5** (registrado pelo orquestrador neste planejamento).
