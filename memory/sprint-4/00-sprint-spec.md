# Sprint 4 — Spec mestre (ultra-plan)

> Orquestrador (ultra-plan). Fonte para PO, Arquiteto, Back, Front, Tester.
> Escopo aprovado pelo dono via gate de planejamento (2026-06-30). Roadmap re-sequenciado: ver §10 e ADR-P10.

## 1. Objetivo (1 frase)
Ao fim deste sprint, **Bruno se inscreve num evento PAGO, paga por um gateway simulado com o dinheiro retido em escrow, e só então recebe o ingresso com QR** — emitido por uma saga assíncrona orientada a eventos (RabbitMQ), idempotente e à prova de concorrência.

## 2. Escopo

| ID | História | Requisito | Por que cabe em ~2 semanas |
|---|---|---|---|
| US-060 | Consumidores RabbitMQ idempotentes (`processed_events`) ligados | RNF (ADR-T04) | Topologia já declarada em `definitions.json`; falta só código (RabbitTemplate + `@RabbitListener` + dedup). É a **fundação** das outras duas. |
| US-040 | Pagar evento pago (gateway **simulado**) com retenção em **escrow** | RF05 | `payment_db` (pagamentos/taxa/repasse) já existe; payment-service vira real reusando o user-service como gabarito. |
| US-041 | Emitir ingresso **só após** `pagamento.aprovado` (saga de inscrição paga) | RF05 | Reusa a reserva atômica de vaga (ADR-T07) e o `UNIQUE(inscricao_id)` em ingressos já existentes; o delta é o estado intermediário + o consumidor. |

**Fora deste sprint (intencional):**
- **US-042 (reembolso)** e **US-043 (repasse −10% pós-evento)** → Sprint 5. Escrow aqui apenas **retém** (`pagamentos.status=CONFIRMADO`); não libera nem estorna.
- US-034 (check-in), US-035 (cancelar inscrição), US-024/025 (avaliações) → sprints seguintes.
- US-061 / RNF09 (testes de carga + observabilidade) → depois.
- Gateway de pagamento **real** (só `SIMULADO`); mTLS inter-serviço (dívida ADR-T08).

## 3. Serviços afetados + delta de modelo de dados (Flyway)

| Serviço | Mudança | Migration |
|---|---|---|
| **payment-service** | Sai de stub → real: entidades `Pagamento`/`Reembolso`(só leitura), consumidor `pedido.criado`, produtor `pagamento.aprovado`, confirmação via gateway simulado, escrow. | **V2**: `processed_events(event_id UUID PK, processado_em)` para idempotência. (pagamentos/reembolsos já em V1.) |
| **ticket-service** | Inscrição em evento PAGO: novo estado intermediário, produtor `pedido.criado`, consumidor `pagamento.aprovado` (emite ingresso). | **V2**: ampliar `inscricoes.status` CHECK para incluir **`PENDENTE_PAGAMENTO`** (e avaliar `EXPIRADA`); `processed_events(event_id UUID PK, ...)`. |
| **event-service** | Expor `preco` (e `promotorId`, para repasse futuro) no resumo interno. | Sem migration (coluna `preco` já existe em V1). Delta só no DTO `EventoInternoResponse`. |
| **infra** | `definitions.json` já tem exchange `ticketeira.events` + filas `pedido.criado`/`pagamento.aprovado` (+ DLX/DLQ). Sem alteração de topologia. | — |

## 4. Endpoints + eventos AMQP previstos

**REST (gateway):**
- `POST /api/inscricoes` (ticket) — passa a aceitar evento **PAGO**: reserva vaga, cria inscrição `PENDENTE_PAGAMENTO`, **não** emite ingresso; retorna a referência de pagamento. (Gratuito: fluxo S3 intacto.)
- `POST /api/payments/{inscricaoId}/confirmar` (payment) — **gateway simulado** confirma o pagamento (escrow), publica `pagamento.aprovado`.
- `GET /api/payments/me` e/ou `GET /api/payments/inscricao/{id}` (payment) — status do pagamento do usuário.
- `GET /internal/events/{id}` (event) — passa a incluir `preco` (e `promotorId`). Autorizado por `X-Internal-Token` (ADR-T08), nunca pelo gateway.

**AMQP (exchange `ticketeira.events`, topic):**
| Routing key | Produtor | Consumidor | Payload (campos-chave) |
|---|---|---|---|
| `pedido.criado` | ticket-service | payment-service | `eventId`(UUID p/ idempotência), `inscricaoId`, `usuarioId`, `eventoId`, `valor` |
| `pagamento.aprovado` | payment-service | ticket-service | `eventId`(UUID), `pagamentoId`, `inscricaoId`, `usuarioId`, `eventoId` |

> Produtor publica em **`afterCommit`** (TransactionSynchronization) — nunca emite evento se a tx local deu rollback.

## 5. Saga de inscrição paga (fluxo + estados)

```
Bruno → POST /api/inscricoes (evento PAGO)
  ticket: validar(PUBLICADO,PAGO) → pre-check dup → reservarVaga (atômico, ADR-T07)
          → Inscricao(PENDENTE_PAGAMENTO)  [SEM ingresso]
          → afterCommit: publica pedido.criado
  payment: consome pedido.criado (idempotente) → cria Pagamento(PENDENTE, escrow)
           valor_bruto=preco · valor_taxa=round(preco*0.10) · valor_repasse=bruto-taxa
Bruno → POST /api/payments/{inscricaoId}/confirmar  (gateway SIMULADO aprova)
  payment: Pagamento → CONFIRMADO (retido/escrow) → afterCommit: publica pagamento.aprovado
  ticket: consome pagamento.aprovado (idempotente) → emite Ingresso(QR, ATIVO)
          + Inscricao → ATIVA
Bruno: ingresso aparece em "Meus ingressos".
```
- **Falha/abandono do pagamento:** vaga fica reservada. Estratégia candidata: **expiração com TTL** (job agendado libera vaga + `Inscricao→EXPIRADA/CANCELADA` para pendentes > N min) **ou** registrar como gap documentado. → **decisão do Arquiteto** (§8 risco R1).
- **Modelo de pagamento:** confirmação **iniciada pelo usuário** (Bruno clica "Pagar"), não auto-aprovação — mais realista e demonstrável na banca. (Arquiteto confirma.)

## 6. Pontos de concorrência + estratégia candidata

| Cenário | Estratégia |
|---|---|
| Última vaga em evento pago (K inscrições simultâneas) | Reserva atômica `UPDATE ... vagas-1 WHERE vagas>0` (ADR-T07) **antes** do pagamento → 1 sucesso, K−1 × 409. |
| `pagamento.aprovado` entregue 2× (at-least-once) | `processed_events(event_id)` no ticket-service + `UNIQUE(inscricao_id)` em ingressos → **1 ingresso só**. |
| `pedido.criado` entregue 2× | `processed_events` no payment-service + `UNIQUE(inscricao_id)` em pagamentos → **1 pagamento só**. |
| Confirmar pagamento 2× | Transição idempotente PENDENTE→CONFIRMADO (no-op se já CONFIRMADO); publica `pagamento.aprovado` 1×. |
| Evento sem rollback fantasma | Produtor publica só em `afterCommit`. |
| Dinheiro | `NUMERIC(12,2)`; taxa = `round(preco*0.10)`; repasse = bruto−taxa (computado, **não liberado**). |

## 7. Dependências entre histórias
`US-060 (infra AMQP + idempotência)` → habilita → `US-040 (payment real: consome pedido.criado, confirma, publica pagamento.aprovado)` → habilita → `US-041 (ticket: estado PENDENTE_PAGAMENTO + consome pagamento.aprovado → emite ingresso)` → `frontend (checkout + pagar + ingresso)`.

## 8. Riscos
- **R1 — Vaga presa em pagamento abandonado:** reserva-antes-de-pagar pode vazar vagas. Mitigação: TTL/expiração (job) **ou** gap documentado. *(Arquiteto decide.)*
- **R2 — Testes com broker real:** `@RabbitListener` não é exercitado por H2/sem-broker. Precisa **Testcontainers RabbitMQ** (além do Postgres), padrão "skip local sem Docker, roda no CI" (lição S3). Risco de tempo/flakiness de CI.
- **R3 — Sprint mais complexo até aqui:** 2 serviços + AMQP + saga + frontend. Mitigação: reembolso/repasse **fora**; caminho-feliz primeiro; payment-service espelha o user-service.
- **R4 — Idempotência/ordenação:** garantir exactly-once-effect com at-least-once. Mitigação: `processed_events` + constraints UNIQUE como rede final.

## 9. Dívidas tocadas
- **ADR-T04 (consumidores RabbitMQ não implementados):** passa a **implementado** (US-060) — todo consumidor idempotente via `processed_events`; produtor em `afterCommit`. Atualizar status no `decisions.md`.
- **ADR-T08 (token interno):** reusado para o `GET /internal/events/{id}` com `preco`.
- **Papel no token (ADR-T01):** já fechado na S1 (US-051) — autorização por papel disponível para proteger endpoints de pagamento se necessário.

## 10. Critérios de sucesso verificáveis
1. Bruno se inscreve em evento **PAGO** → vê "pagamento pendente" → paga (simulado) → **ingresso com QR** aparece em "Meus ingressos". Evento **GRATUITO**: fluxo S3 intacto.
2. Ingresso é emitido **somente** após `pagamento.aprovado` (nunca antes/sem pagamento).
3. `pagamento.aprovado` reentregue (at-least-once) → **1 ingresso só** (idempotência comprovada em teste).
4. Concorrência: K inscrições pagas na última vaga → 1 reserva, K−1 × 409; pagamento confirmado 1×. **Em Postgres + RabbitMQ reais** (Testcontainers/CI verde).
5. Escrow: `pagamentos.status=CONFIRMADO` (retido), `valor_taxa`=10%, `valor_repasse` computado e **não** liberado.
6. CI verde (back `./mvnw verify` + front `npm test`); Swagger do payment-service publicado.

## 11. ADRs previstos (Arquiteto, em `desenvolver-sprint`)
- **ADR-Txx — Saga de inscrição paga**: orientada a eventos, estado `PENDENTE_PAGAMENTO`, escrow retido, confirmação iniciada pelo usuário, política de expiração da reserva.
- **ADR-Txx — Idempotência de consumidores**: `processed_events(event_id UUID)` + publish `afterCommit`; atualização do ADR-T04.
- **ADR-P10 — Escopo do Sprint 4** (registrado já pelo orquestrador neste planejamento).
