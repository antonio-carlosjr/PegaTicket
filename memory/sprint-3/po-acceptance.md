# Sprint 3 — Aceite do PO (Inscrição & Ingresso QR)

> Fase 7. Base: [`test-report.md`](test-report.md) (gate ✅ 14/14) + [`bugs.md`](bugs.md) (3 P1 corrigidos) + `mvnw verify` BUILD SUCCESS.

## Histórias

| História | Status | Evidência |
|---|---|---|
| **US-030** — inscrever-se em evento gratuito + receber ingresso | ✅ **ACEITA** | Smoke T1: 5 inscrições criadas, cada uma com ingresso `codigo_unico`. Mini-saga (validar→reservar→tx local) funcional ponta a ponta. |
| **US-031** — capacidade + sem dupla inscrição (concorrência) | ✅ **ACEITA** | **Gate inegociável**: T1 = 20 paralelas → 5 OK / 15 `EVENTO_ESGOTADO`, `vagas=0` nunca negativo. T2 = dupla concorrente → 1 OK / 1 `JA_INSCRITO`, sem duplicata, compensação sem vazamento. |
| **US-032** — ingresso único com QR | ✅ **ACEITA** | 5 ingressos com `codigo_unico` distinto (`UNIQUE(inscricao_id)` + UUID v4, ADR-T09); front renderiza QR de `codigoUnico` (`qrcode.react`). |
| **US-033** — "meus ingressos" + histórico paginado | ✅ **ACEITA** | `GET /tickets/me` (lista) + `GET /tickets/inscricoes/me` (paginado, `inscritoEm,desc`); telas `MeusIngressos`/`MinhasInscricoes` com estados de UI. |

## Critérios de sucesso verificáveis (§12 da spec)

1. Inscrição gratuita → ingresso QR → "meus ingressos" — ✅
2. 2ª inscrição mesmo evento → **409 JA_INSCRITO** — ✅
3. Capacidade N: N OK, (N+1)ª → **409 esgotado** — ✅
4. **Concorrência última vaga: exatamente N sucessos, resto 409, sem overbooking** — ✅ (núcleo)
5. Exatamente 1 ingresso por inscrição — ✅
6. `mvnw verify` verde (incl. concorrência) + front sem erro de tipo — ✅

## Definition of Done

- [x] ticket-service real (inscrição + ingresso QR + listagens); stubs 501 removidos.
- [x] event-service com `reservar/liberar-vaga` atômicos + `GET /internal/events/{id}` (validação interna).
- [x] **Testes de concorrência verdes** — Testcontainers (rodam no CI Linux) **+** smoke integrado em Postgres real 14/14. Gate inegociável cumprido.
- [x] Front: inscrever + meus-ingressos (QR) + histórico, estados de UI.
- [x] `mvnw verify` verde; commits atômicos `tipo(US-id)`.
- [x] ADRs P09/T07/T08/T09 registradas.
- [ ] CI verde + PR + retrospectiva → **`/validar-sprint 3`** (próxima etapa).

## Ressalvas / follow-ups (não bloqueiam o aceite)
- **Aprendizados a promover** ao `coding-standards.md` (rota inexistente→404; reads cross-service só via `/internal/**`; wiring service-to-service no compose) — formalizar no `/validar-sprint 3`.
- **Segurança do token interno:** hoje `dev-internal-secret` (default). Em prod, `INTERNAL_TOKEN` deve vir do ambiente (Railway) — já parametrizado, só configurar.
- Bug pré-existente Sprint 1 (label "E-mail" duplicado) segue em tarefa separada.

## Veredito
**Sprint 3 — Inscrição & Ingresso QR: APROVADA.** As 4 histórias aceitas; o gate de concorrência (DoD inegociável) provado contra Postgres real. Pronta para `/validar-sprint 3` (code review adversarial + PR).
