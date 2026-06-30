# Sprint 5A — Validação PO da Arquitetura (Trilha Financeiro)

> Papel: Product Owner. Validação de produto — **não** de design técnico.
> Referências: `po-planning.md` (criterios US-042/US-043), `00-sprint-spec.md` §2/§5/§6,
> `architecture.md`, `api-contracts.md`, `data-model.md`, `tests-spec.md`, `decisions.md` (ADR-T12, ADR-T13).

---

## Histórias cobertas

| História | Veredicto | Justificativa |
|---|---|---|
| **US-043** — Repasse (−10%) após evento REALIZADO | ✅ **Coberta** | Endpoint `POST /events/{id}/encerrar` (PROMOTOR+owner) → `PUBLICADO→REALIZADO` → `evento.finalizado` → payment: cada `pagamento CONFIRMADO` → `REPASSADO` com `valor_repasse` já computado no S4. Marina vê `REPASSADO` em `/api/payments/me` sem relogar. Idempotência via `processed_events`. Cobre todos os 4 critérios de aceite. |
| **US-042** — Reembolso por evento cancelado (em massa) | ✅ **Coberta (parte 5A)** | `POST /events/{id}/cancelar` (já existe) passa a publicar `evento.cancelado` → payment: `CONFIRMADO→REEMBOLSADO` + `reembolsos(EVENTO_CANCELADO)` → ticket: `inscricoes ATIVA→CANCELADA`, `ingressos→CANCELADO`. Bruno vê `REEMBOLSADO` no extrato. Critérios 1, 4 e 5 da US-042 cobertos nesta trilha. Reembolso individual (critério 2, `CANCELAMENTO_PARTICIPANTE`) corretamente adiado para 5B — ver D3 abaixo. |

---

## Aderência ao escopo

- [x] Não introduz feature fora do roadmap (RF06; ADR-P11)
- [x] `TECH-S4-01` (`evento_id`/`promotor_id` em `pagamentos`) embutido — corrige gap do S4 que bloquearia o repasse; escopo legítimo desta trilha
- [x] Reembolso `CANCELAMENTO_PARTICIPANTE` **não** entra na 5A — arquiteto respeitou o recorte do PO (US-035 = 5B)
- [x] Job agendado de finalização documentado como evolução futura, **não** entregue aqui — correto para a demo da banca
- [x] UI do botão "Encerrar evento" descrita como desejável/opcional — sem comprometer os critérios de produto (extrato já reflete os status)
- [x] Sem gateway de pagamento real, sem notificações e-mail de repasse/reembolso — intencional (fora do sprint per `po-planning.md` §Fora deste sprint)

---

## Cenários exigidos pelo PO — verificação

| Cenário PO | Coberto? | Onde |
|---|---|---|
| Marina vê o repasse após encerrar o evento | ✅ | `architecture.md` fluxo REPASSE; `api-contracts.md` §2; `tests-spec.md` B2.a + B1.b (extrato) |
| Bruno vê `REEMBOLSADO` após promotor cancelar | ✅ | `architecture.md` fluxo REEMBOLSO EM MASSA; `tests-spec.md` C1.a |
| Vagas liberadas (volta à capacidade) no cancelamento | ✅ (com D2 abaixo) | reset `vagasDisponiveis = capacidade` em `Evento.cancelar()` aprovado — `tests-spec.md` D1.e |
| Idempotência: reentrega → aplica 1× | ✅ | `processed_events` em ambos consumidores; `tests-spec.md` B2.b (repasse), C1.b (reembolso), D1.b (ticket) |
| Corrida repasse-vs-reembolso: só um vence | ✅ | Transições condicionais `WHERE status='CONFIRMADO'` + row lock; `tests-spec.md` C2 (`@RepeatedTest(3)`) |
| Pagamento não-CONFIRMADO não é tocado | ✅ | US-043 critério 4; cláusula `status='CONFIRMADO'` em todos os updates; `tests-spec.md` B2.c, C1.c |
| Admin audita REPASSADO e REEMBOLSADO | ✅ | `GET /api/payments?status=REPASSADO/REEMBOLSADO`; `tests-spec.md` C1.d |

---

## Decisões de produto (PO decide, Arquiteto pediu parecer)

### D1 — Endpoint `POST /events/{id}/encerrar` como gatilho REALIZADO

**Decisão: APROVADO.**
O endpoint é suficiente e superior ao job para a demo/banca da 5A: Marina clica "Encerrar" e vê o repasse no extrato em segundos — demonstrável, determinístico, sem dependência de relógio. O job (`@Scheduled`, mesmo padrão do `ExpiracaoReservaJob`/S4) fica documentado como evolução futura. Para o escopo acadêmico, o endpoint basta e não introduz não-determinismo nos testes.

### D2 — Resetar `vagas_disponiveis = capacidade` ao cancelar evento

**Decisão: APROVADO — realizar o reset.**
O critério de aceite de US-042 diz explicitamente "vagas são liberadas (contagem volta à capacidade)". Embora o status CANCELADO já impeça novas reservas, deixar `vagas_disponiveis` com valor residual cria inconsistência visível no banco que pode confundir a auditoria do Admin e qualquer relatório futuro. O reset é 1 linha local em `Evento.cancelar()`, sem I/O cross-service — custo zero, banco coerente, critério satisfeito literalmente.

### D3 — Escopo do reembolso na 5A: somente `EVENTO_CANCELADO`

**Decisão: CONFIRMADO.**
O recorte é o correto. `CANCELAMENTO_PARTICIPANTE` (reembolso individual, US-035) depende da política de prazo (D2 do po-planning) e do ciclo de cancelamento do participante, que são escopo da Trilha 5B junto com check-in e avaliações. Misturar os dois caminhos na 5A introduziria dependência de US-035 antes que o ciclo completo de cancelamento (ingresso, inscrição, vaga, prazo) esteja definido na 5B. O mecanismo projetado (`Reembolso.criar`, `reembolsar()`, campo `motivo`) já é reutilizável para a 5B sem reescrita — boa separação de responsabilidade.

### D4 — Reembolso por evento cancelado é integral, sem restrição de prazo

**Decisão: CONFIRMADO.**
Reembolso de `EVENTO_CANCELADO` é sempre 100% do `valor_bruto`, sem janela de prazo. O prazo (`prazo_reembolso_dias`) aplica-se **exclusivamente** ao cancelamento voluntário do participante (D2 do po-planning, US-035/5B). Se Marina cancela o evento, a culpa é do promotor — Bruno recebe o valor inteiro, sem questionamento de data. Isso está registrado em `po-planning.md` §D2 e refletido corretamente no campo `valor = p.valor_bruto` do `Reembolso.criar` (data-model.md §A).

---

## Pontos de atenção / ressalvas

### RA1 — UNIQUE parcial em `reembolsos` (opcional comentado na migration)

O `data-model.md` deixa comentado o índice `uk_reembolso_evento_cancelado ON reembolsos(pagamento_id) WHERE motivo='EVENTO_CANCELADO'`. A proteção primária já é o `processed_events` (PK colisão na reentrega); o UNIQUE parcial cobre reprocessamento manual com `eventId` diferente (risco baixo, `architecture.md` §Idempotência). **Decisão do PO: incluir o índice.** Custo zero em DDL, sem impacto em performance, e fecha o gap de defesa em profundidade sem complicar o código. Uma auditoria financeira que encontre dois registros `reembolsos` para o mesmo pagamento com `EVENTO_CANCELADO` é um incidente de produto — vale a salvaguarda.

### RA2 — `evento_id` nulo em pagamentos legados (pré-V3)

`architecture.md` §Riscos documenta que pagamentos anteriores à migration V3 terão `evento_id = NULL` e não serão cobertos pelo repasse/reembolso. Para o escopo demo/banca isso é aceitável (todos os pagamentos de teste são criados pós-V3). **O PO exige que o Admin veja claramente quais pagamentos têm `evento_id = NULL`** — garantir que `GET /api/payments` (admin) exiba o campo `eventoId` (já previsto em `api-contracts.md` §2) para que a auditoria identifique pagamentos legados sem ambiguidade.

### RA3 — UI do botão "Encerrar evento" (desejável, não bloqueante)

`architecture.md` §Serviços afetados e `tests-spec.md` §E marcam o botão "Encerrar evento" no frontend como **opcional** para a 5A. O PO endossa: o critério US-043.2 ("Marina vê o repasse sem relogar") pode ser verificado via `GET /payments/me` mesmo sem botão novo, desde que o endpoint REST esteja funcionando. **Porém, para a demo presencial na banca, o botão é altamente recomendado** — sem ele, Marina precisa usar curl para encerrar o evento, o que quebra a narrativa. O PO pede que o Dev/Frontend entregue o botão como parte da 5A; se não couber no tempo, o PO aceita a entrega mínima (API + extrato) e registra o botão como dívida de UX para a 5B.

---

## Aprovação

```
[x] APROVADO COM RESSALVAS
[ ] APROVADO
[ ] REVISAR
```

**A arquitetura da Trilha 5A está aprovada para implementação**, com as seguintes condições registradas acima:

1. **D2 aceito:** resetar `vagas_disponiveis = capacidade` em `Evento.cancelar()` (1 linha; banco coerente com critério literal do PO).
2. **RA1:** ativar o UNIQUE parcial `uk_reembolso_evento_cancelado` na migration (descomentar no `data-model.md` / `V3__repasse_reembolso.sql`).
3. **RA2:** confirmar que `eventoId` aparece em `GET /api/payments` (admin) para distinguir pagamentos legados — já previsto no contrato, sem novo trabalho de backend.
4. **RA3:** entregar o botão "Encerrar evento" no frontend como parte da 5A (não bloqueante para o aceite, mas fortemente recomendado para a demo da banca).

As ressalvas 1–3 são de baixíssimo custo e não alteram a arquitetura. A ressalva 4 é de UX, não de produto — não bloqueia o desenvolvimento backend.

**Veredicto por história:**
- US-043 (repasse): ✅ Aprovada — todos os critérios cobertos
- US-042 (reembolso em massa por evento cancelado): ✅ Aprovada (parte 5A) — critérios 1, 4 e 5 cobertos; critérios 2/3 corretamente na 5B
