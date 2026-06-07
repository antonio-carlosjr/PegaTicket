# Sprint 3 — Inscrição & Ingresso QR (gratuito) · Spec Mestre (ultra-plan)

> Gerada por `/planejar-sprint 3`. Depende do **Sprint 2** (eventos publicados). **Sprint de maior risco técnico (concorrência do abre-vendas).**
> Lê antes: [`architectural-plan.md`](../project/architectural-plan.md) (§7 concorrência), [`backlog.md`](../project/backlog.md) (Épico B), `ticket-service/.../V1__init.sql`, `docs/api/ticket-service.yaml`. Gabarito: `user-service`.

---

## 1. Objetivo (1 frase)
> **Ao fim deste sprint, o Participante se inscreve num evento gratuito publicado, o sistema respeita a capacidade e impede dupla inscrição, emite um ingresso único com QR, e o participante vê "meus ingressos" e histórico.** (Roadmap §8 / RF03, RF04, RF09.)

## 2. Escopo — histórias
| ID | História | Cabe porque… |
|---|---|---|
| **US-030** | Como participante, quero me **inscrever num evento gratuito e receber meu ingresso** | caminho-feliz síncrono (sem dinheiro) |
| **US-031** | …com **controle de capacidade e sem dupla inscrição** | concorrência — núcleo do sprint |
| **US-032** | Como participante, quero **ingresso único com QR** após confirmação | `UNIQUE(inscricao_id)` + `codigo_unico` |
| **US-033** | Como participante, quero ver **"meus ingressos"** e histórico | leitura paginada |

> Caminho **pago** (escrow/saga AMQP) é a **Sprint 4**. Check-in (US-034) e cancelamento (US-035) são a **Sprint 5**.

## 3. Serviços afetados
| Componente | Mudança |
|---|---|
| `services/ticket-service` | **vira real**: `@Entity Inscricao/Ingresso`, repos, `InscricaoService`, `TicketController`; cliente REST para o event-service |
| `services/event-service` | **endpoints internos** `reservar-vaga`/`liberar-vaga` (decremento atômico de `vagas_disponiveis` — preparado na Sprint 2) |
| `frontend` | botão Inscrever-se (gratuito) no detalhe + tela "Meus ingressos" (QR) + histórico |

## 4. Modelo de dados
- `ticket_db` já tem: `inscricoes`(**UNIQUE(usuario_id, evento_id)**, status ATIVA/CANCELADA), `ingressos`(inscricao_id **UNIQUE**, `codigo_unico` **UNIQUE**, status ATIVO/UTILIZADO/CANCELADO), `checkins`. **Sem delta** necessário (check-in é Sprint 5).
- `event_db`: usa `vagas_disponiveis` (Sprint 2).
- **Sem AMQP nesta sprint** (fluxo gratuito é síncrono); `processed_events` entra na Sprint 4.

## 5. Concorrência (o coração — declarar e TESTAR)
| Cenário | Estratégia |
|---|---|
| **Dupla inscrição** (mesmo usuário+evento) | `UNIQUE(usuario_id, evento_id)`: tentar inserir e tratar `DataIntegrityViolationException` → **409** "Você já está inscrito" |
| **Última vaga** (N inscrições simultâneas) | event-service: `UPDATE eventos SET vagas_disponiveis = vagas_disponiveis - 1 WHERE id=? AND vagas_disponiveis > 0` e checar **rowsAffected** (1=reservou, 0=esgotado→409) — decremento atômico, sem race |
| **Emitir ingresso 1×** | `UNIQUE(inscricao_id)` + `codigo_unico` UNIQUE; inscrição+ingresso na **mesma transação local** do ticket-service |

### Mini-saga síncrona (cross-service, sem transação distribuída)
```
ticket-service.inscrever(eventoId, usuarioId):
  1. valida evento (GET event-service): existe, PUBLICADO, tipo=GRATUITO
  2. reservar vaga (POST event-service /reservar-vaga) → se esgotado: 409
  3. tx local: cria inscricao(ATIVA) + ingresso(codigo_unico, ATIVO)
       └─ se UNIQUE(usuario,evento) violar (dup): COMPENSA → POST /liberar-vaga; 409 "já inscrito"
  4. retorna ingresso (com codigo_unico p/ QR)
```
> Compensação obrigatória se o passo 3 falhar após o passo 2 (libera a vaga). Arquiteto fixa a ordem definitiva e timeouts/retry do cliente REST.

## 6. Endpoints (detalhe em `api-contracts.md`)
| Método | Rota | Auth | O quê |
|---|---|---|---|
| POST | `/api/tickets/inscricoes` | autenticado | inscreve em evento **gratuito** (`{eventoId}`) → 201 ingresso; 409 já-inscrito/esgotado; 422 evento pago/não publicado |
| GET | `/api/tickets/me` | autenticado | meus ingressos (com `codigo_unico`, evento, status) |
| GET | `/api/tickets/inscricoes/me` | autenticado | histórico de inscrições (paginado) |
| POST | `/events/{id}/reservar-vaga` | **interno** (ticket→event) | decremento atômico; 200/409 esgotado |
| POST | `/events/{id}/liberar-vaga` | **interno** | compensação (+1 até capacidade) |
> `reservar/liberar` são **service-to-service** na rede do Docker, **não roteados publicamente** pelo gateway. Autorização inter-serviço (segredo compartilhado/header) é decisão do Arquiteto (ADR-T08); no MVP, isolamento de rede + não-exposição no gateway.

## 7. Ingresso & QR
- `codigo_unico`: UUID v4 (ou HMAC-assinado — Arquiteto decide; assinado facilita validação anti-forja no check-in da Sprint 5).
- **Frontend renderiza o QR** a partir do `codigo_unico` (lib JS de QR — nova dependência justificada em `frontend-log.md`). Backend não gera imagem.

## 8. Frontend
- **Detalhe do evento:** botão **"Inscrever-se"** (só GRATUITO nesta sprint) → sucesso mostra o **ingresso com QR**; erros claros (já inscrito / esgotado).
- **"Meus ingressos":** lista com QR + dados do evento + status. **Histórico** de inscrições.
- Mostrar **vagas reais** no detalhe (vem de `vagas_disponiveis`).

## 9. Dependências
US-030 (inscrição) é base; US-031 (capacidade/dup) endurece-a; US-032 (QR) é parte da emissão; US-033 (listagem) lê o resultado. Tudo depende de **eventos publicados (Sprint 2)**.

## 10. Riscos & mitigação
| Risco | Prob. | Impacto | Mitigação |
|---|---|---|---|
| Race na última vaga (overbooking) | **Alta** | **Alto** | decremento atômico com `WHERE vagas>0` + checar rowsAffected; **teste de carga concorrente obrigatório** |
| Falha parcial cross-service (event-service cai após reservar) | Média | Alto | compensação (`liberar-vaga`) + timeout/retry idempotente; registrar inconsistência p/ reconciliação |
| Dupla emissão de ingresso | Baixa | Alto | `UNIQUE(inscricao_id)` + mesma tx |
| `reservar-vaga` exposto indevidamente | Média | Alto | não rotear no gateway; ADR-T08 (autorização inter-serviço) |
| QR previsível/forjável | Média | Médio (S5) | `codigo_unico` aleatório/assinado; validação real no check-in (Sprint 5) |

## 11. Fora de escopo (intencional)
Caminho **pago**/escrow/saga AMQP (Sprint 4) · **check-in**/validação na porta (Sprint 5) · **cancelamento** de inscrição + reembolso (Sprint 5) · avaliações · notificações por e-mail de inscrição (nice-to-have futuro).

## 12. Critérios de sucesso verificáveis
1. Participante se inscreve em evento gratuito → recebe **ingresso com QR**; aparece em **"meus ingressos"**.
2. Segunda inscrição no mesmo evento → **409 "já inscrito"**.
3. Capacidade = N: N inscrições OK; a **(N+1)ª → 409 "esgotado"**.
4. **Concorrência:** K requisições simultâneas na **última vaga** → **exatamente 1** sucesso, K-1 × 409 (sem overbooking).
5. Cada inscrição tem **exatamente 1 ingresso**.
6. `./mvnw verify` verde (incl. testes de concorrência); front sem erro de tipo.

## 13. Testes-chave (Arquiteto detalha; Tester implementa)
- **Race última vaga:** `Promise.allSettled`/threads → 1 fulfilled, resto 409; `vagas_disponiveis` final = 0, nunca negativo.
- **Dupla inscrição concorrente:** 1 sucesso, 1 × 409.
- **Compensação:** falha no passo 3 → `vagas_disponiveis` é restaurada.
- **Ingresso único** por inscrição (tentar emitir 2× falha).
- **Evento pago/não publicado** → 422 (bloqueado nesta sprint).

## 14. Decisões a registrar (ADR)
- **ADR-P09** — escopo Sprint 3 (Inscrição gratuita + ingresso QR).
- **ADR-T08** — autorização inter-serviço (ticket→event `reservar/liberar`): no MVP, isolamento de rede + não-exposição no gateway; evoluir para segredo/mTLS depois.
- **ADR-T09** — `codigo_unico` do ingresso: estratégia (UUID vs HMAC-assinado) e impacto no check-in (Sprint 5).

## 15. Definition of Done
- [ ] ticket-service real (inscrição gratuita + ingresso QR + listagens); stubs removidos.
- [ ] event-service com `reservar/liberar-vaga` atômicos.
- [ ] **Testes de concorrência verdes** (última vaga + dupla inscrição) — gate inegociável.
- [ ] Front: inscrever + meus-ingressos (QR) + histórico, estados de UI.
- [ ] `./mvnw verify` + CI verdes; commits atômicos; PR.
- [ ] ADRs; backlog; retrospectiva.
