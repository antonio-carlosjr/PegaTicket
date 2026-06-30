# Sprint 4 — Aceite do PO

> Product Owner: PO Sênior (sonnet). Data: 2026-06-30.
> Artefatos consultados: `po-planning.md`, `po-validation.md`, `test-report.md`, `bugs.md`.

---

## Veredictos por história

| História | Veredicto | Cenário verificado | Motivo (se ❌ / condição se ⚠️) |
|---|---|---|---|
| **US-060** — Consumidores RabbitMQ idempotentes (`processed_events`) | ✅ (condicional ao CI) | Unit verde: `afterCommit` não publica em rollback (payment 27/27, ticket 44/44 sem falhas). `processed_events` PK bloqueia segunda escrita — verificado por teste unitário com mock. Critério 1 e 3 comprovados localmente. Critério 2 (RabbitMQ real, Testcontainers) **aguarda CI verde** — invariantes A3.b e B1.b pulados local. | Aceite pleno condicionado à execução de A3.b e B1.b no CI com Docker. Se qualquer um falhar → vira P0 e o critério 2 fica ❌. |
| **US-040** — Pagamento pago via gateway simulado / escrow | ✅* (critério 3 parcial — ver parecer abaixo) | Critério 1 (status `PENDENTE_PAGAMENTO`, sem ingresso): unit verde + frontend 74/74 (`CheckoutPage` exibe "Pague em até 30 min"). Critério 2 (escrow `CONFIRMADO`, `valor_taxa=10%`, `valor_repasse` correto): unit verde (`payment` 27/27, inclui HALF_UP e `confirmar()` idempotente). Critério 3 (Admin audita via UI): endpoint `GET /api/payments` existe e é auditável via Swagger/API; tela admin **não construída** (TECH-S4-02). Critério 4 (concorrência última vaga — Postgres real): **aguarda CI** (A2 pulado local). Critério 5 (idempotência de confirmação — `PENDENTE→CONFIRMADO` no-op se já confirmado): unit verde (B2.c coberto em unit). | Critério 3 marcado ⚠️ (ver parecer). Critério 4 condicional ao CI (A2). |
| **US-041** — Ingresso somente após `pagamento.aprovado` (saga assíncrona) | ✅ (condicional ao CI nos cenários de saga) | Critério 1 (QR aparece em "Meus ingressos" após saga; polling 60s): frontend 74/74 verde — `MeusIngressosPage` com polling + timeout + mensagem orientadora. Critério 2 (sem `pagamento.aprovado` → nenhum ingresso): unit verde no ticket-service. Critério 3 (idempotência de ingresso — `pagamento.aprovado` 2×): **aguarda CI** (A3.b, Testcontainers RabbitMQ+PG). Critério 4 (fluxo GRATUITO S3 intacto): regressão ticket-service verde (44/44, 12 pulados por Docker — os 12 são os de integração, não o GRATUITO que é unit). Critério 5 (Marina vê `PENDENTE_PAGAMENTO` vs `ATIVA` separado): coberto em A3.a, aguarda CI. | Critérios 3 e 5 condicionais ao CI. |

---

## Parecer sobre US-040 — Critério 3: tela admin de pagamentos (TECH-S4-02)

**Decisão PO: ⚠️ PARCIAL — suficiente para o MVP, não para entrega completa.**

O Admin consegue auditar os pagamentos em escrow **via endpoint REST** (`GET /api/payments`), autenticado por papel ADMIN, com os três campos de valor (`valor_bruto`, `valor_taxa`, `valor_repasse`) e rastreabilidade completa (`inscricaoId`, `usuarioId`). O teste B5 (auth do endpoint) está pendente de CI, mas o contrato está correto.

**O que falta:** a tela admin no frontend. Sem ela, o Admin (pessoa real no balcão) não consegue auditar sem ferramentas de desenvolvedor (Swagger/curl). Para um MVP acadêmico onde o fluxo principal é de Bruno e Marina, e o Admin é um papel técnico com acesso à API, considero aceitável. Mas não é a experiência planejada no po-planning.md ("Admin consulta a listagem de pagamentos" — implica UI).

**Consequência prática:** o critério US-040.3 fica ⚠️ (auditoria via API comprovada; UI pendente). O sprint não é reprovado por isso, mas TECH-S4-02 deve entrar no backlog de S5 com prioridade alta — o Admin sem UI perde a capacidade de rastrear pagamentos dia-a-dia sem assistência técnica.

---

## Ressalvas das validações anteriores — status final

| Ressalva | Status |
|---|---|
| Ressalva 1 — Checkout exibe "Pague em até 30 min" | ✅ **Atendida** — `CheckoutPage` exibe o aviso (frontend 74/74 verde, inclui teste de timeout). |
| Ressalva 2 — Polling com timeout de 60s e mensagem orientadora | ✅ **Atendida** — implementado e coberto nos 74 testes de frontend. |
| Ressalva 3 — CI com Docker obrigatório para testes críticos (A3, B1) | ⏳ **Pendente** — os testes críticos de integração (A3.b, B1.b, A2) rodam **só no CI** (GitHub Actions / ubuntu). O aceite de US-060 critério 2, US-040 critério 4, e US-041 critérios 3 e 5 é **condicional ao CI verde**. |

---

## Follow-ups técnicos (não bloqueiam S4)

| ID | Descrição | Destino |
|---|---|---|
| TECH-S4-01 | `pagamentos` não persiste `evento_id`/`promotor_id` — necessário para S5 (repasse) | Backlog S5 |
| TECH-S4-02 | Tela admin de pagamentos não construída — endpoint existe; UI pendente | **Backlog S5 — prioridade alta** |
| TECH-S4-03 | `IngressoPendenteCard` não exibe prazo restante real (falta `inscritoEm` no response) | Backlog S5 |

---

## Histórias devolvidas ao backlog

_Nenhuma._ As três histórias estão aceitas (US-040 com critério 3 parcial, registrado como TECH-S4-02).

---

## Veredicto geral do Sprint 4

> **ACEITO COM RESSALVAS — confirmar no CI via `/validar-sprint 4`**

**Fundamento:**

- A suíte executável localmente está integralmente verde: 0 falhas em unit (event/ticket/payment), regressão GRATUITO verde, 74/74 no frontend.
- Os invariantes de **concorrência** (última vaga PAGO), **idempotência de saga** (`pagamento.aprovado` e `pedido.criado` 2×) e **escrow** (B2.a-f) foram especificados com cobertura rastreável e executados em unit/mock onde possível; a camada Testcontainers (RabbitMQ + Postgres real) é a única lacuna, e é estrutural ao ambiente Windows — não é evidência de defeito.
- Os três critérios de UX do PO (ressalvas 1 e 2) foram atendidos: checkout com aviso de 30 min, polling com timeout de 60s e mensagem orientadora.
- O único ponto de produto incompleto é a tela admin (TECH-S4-02), registrado como dívida de S5, aceitável no MVP.

**Condição para aceite pleno:** CI (GitHub Actions) deve rodar `/validar-sprint 4` com Docker disponível e retornar verde nos casos A2, A3.a/b/c, B1.a/b, B2.*, A4 e B5. Se qualquer caso falhar, o item retorna ao loop de bug e o sprint fica em **ACEITO COM RESSALVAS ABERTAS** até a correção.
