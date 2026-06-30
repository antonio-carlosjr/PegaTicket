# Sprint 4 — Validação PO da Arquitetura

> Gerado pelo PO após revisão de `architecture.md`, `api-contracts.md`, `data-model.md`,
> `tests-spec.md`, `decisions.md` (ADR-T10, ADR-T11, ADR-T04 atualizado).
> Data: 2026-06-30.

---

## Histórias cobertas

- **US-060** ✅ — Consumidores idempotentes (`processed_events`) completamente especificados em ambos os serviços; `afterCommit` impede publicação em rollback; testes críticos A3.b, B1.b, A5 e B4 cobrem os três critérios do PO.
- **US-040** ✅ — Fluxo pago completo (inscrição → escrow → confirmação 1 toque → auditoria admin) coberto nos contratos, modelo de dados e testes. Todos os cinco critérios de aceite têm cobertura rastreável no mapa de testes.
- **US-041** ✅ — Emissão de ingresso exclusivamente após `pagamento.aprovado` garantida pela saga; estado `PENDENTE_PAGAMENTO` bem delimitado; fluxo GRATUITO da S3 explicitamente preservado (teste A6/C2.b).

---

## Aderência ao escopo

- [x] Nenhuma feature fora do roadmap: reembolso (US-042) e repasse ao promotor (US-043) estão **fora** — escrow aqui apenas retém (`CONFIRMADO`, sem liberação).
- [x] `valor_repasse` é **computado e não liberado** (nenhum movimento de repasse/reembolso registrado no S4; confirmado no `tests-spec.md` §B2.a).
- [x] Gateway permanece **SIMULADO** — integração Stripe/PagSeguro não entra.
- [x] Check-in (US-034), cancelamento (US-035), avaliações (US-024/025) e testes de carga (US-061) ficam fora, confirmados no spec.
- [x] `StatusPagamento` inclui `REEMBOLSADO` e `REPASSADO` **como enum read-only** — coerente: a migração V1 já existia; nenhuma escrita acontece nesses estados no S4. Não vaza S5.
- [x] Endpoint `GET /api/payments` (auditoria admin) autoriza por `X-User-Papel == ADMIN` — o gateway já injeta esse header (`JwtAuthGlobalFilter.java:67`, confirmado pelo PO). **Não é dívida bloqueante.**

---

## Análise por ator

### Bruno (participante, celular, com pressa)

O fluxo de compra está direto:

1. `POST /api/inscricoes` → resposta 201 com `status=PENDENTE_PAGAMENTO` e referência de checkout.
2. Frontend redireciona para `/checkout/:inscricaoId` — tela exibe "Pagamento pendente" + botão "Pagar".
3. Bruno toca "Pagar" (1 toque, sem corpo de request) → `POST /api/payments/{inscricaoId}/confirmar`.
4. Frontend faz polling de "Meus ingressos" (ou `GET /api/payments/inscricao/{id}`) até o ingresso aparecer — **sem reload manual** (critério R3/US-041.1, coberto por C1.c).
5. Erro de gateway → 402 `PAGAMENTO_RECUSADO` → mensagem amigável + "Tentar de novo" (C2.a).

Julgamento: **fluxo simples o suficiente para Bruno no celular.** A única fricção é a espera assíncrona da saga; o polling do frontend mitiga isso com o estado "aguardando confirmação". O erro 404 (pagamento ainda não criado porque `pedido.criado` não chegou) é um caso de race condition real que pode confundir o usuário se o polling começar antes de o payment-service consumir a fila — mas é janela de milissegundos e o polling absorve. Aceitável para o escopo acadêmico.

### Marina (promotora)

- O critério US-041.5 ("Marina vê inscrito como ATIVO só após emissão do ingresso") está mapeado (A3.a + listagem por status).
- O painel de eventos PAGOS exibirá a contagem separada por status (`PENDENTE_PAGAMENTO` vs `ATIVA`).
- Fluxo GRATUITO da S3 intacto — Marina não perde funcionalidade existente.

### Admin (auditoria de escrow)

- `GET /api/payments` com paginação, filtro por `status`, e os três campos de escrow (`valor_bruto`, `valor_taxa`, `valor_repasse`) visíveis por papel ADMIN (B5).
- `inscricaoId` e `usuarioId` presentes no `PagamentoResponse` — rastreabilidade completa.
- Nenhum movimento de repasse registrado no S4 — escrow puro.

---

## Parecer sobre o TTL de 30 minutos (ADR-T10)

**Aceitável de produto, com ressalva de comunicação ao usuário.**

A política de TTL de 30 min para a vaga reservada (Bruno tem 30 min para confirmar o pagamento após se inscrever) é razoável para o contexto: eventos acadêmicos/pequenos não têm a pressão de venda de shows de grande porte, e 30 min é um tempo generoso para um fluxo de "1 toque" num gateway simulado.

Riscos reais mapeados:

- **Bruno abre a tela, vai fazer outra coisa e volta após 30 min:** a vaga é liberada (`EXPIRADA`) e o botão "Pagar" retorna 409 `INSCRICAO_EXPIRADA`. Do ponto de vista de produto, isso é aceitável **se a tela de checkout informar claramente o tempo restante** (ex.: "Você tem X min para pagar"). A arquitetura não define esse detalhe de UX — é uma ressalva de produto (ver abaixo).
- **Confirmação tardia chega após expiração (R6):** o endpoint retorna 409 sem emitir ingresso. Documentado no ADR-T10 como aceitável. O PO concorda: o fluxo é rebuild (Bruno se inscreve de novo se houver vagas), o que é padrão no mercado.
- **Job de expiração concorre com confirmação tardia:** tratado pelo lock pessimista e pela transição idempotente. Sem risco de duplicidade de ingresso.

O ADR-T10 escolheu o TTL sobre "gap documentado" — o PO concorda com essa escolha: vaga presa indefinidamente é pior do que uma expiração com feedback claro.

---

## Pontos de atenção (ressalvas)

### Ressalva 1 — UX: exibir contador regressivo na tela de checkout (não bloqueante)

A `CheckoutPage.tsx` não menciona exibição do tempo restante para o Bruno. Se o usuário não sabe que tem 30 min, a expiração será percebida como bug. **Recomendação:** mostrar "Pague em até 30 min para garantir sua vaga" (ou um contador regressivo simples baseado em `inscritoEm` + TTL). Não bloqueia o sprint — pode ser ajustado na implementação frontend sem mudar contratos.

### Ressalva 2 — Polling: ausência de timeout ou limite de tentativas definido (não bloqueante)

O polling de "Meus ingressos" após a confirmação do pagamento não tem um limite de tentativas/tempo especificado nos contratos ou na spec de frontend. Se a saga falhar silenciosamente (ex.: `pagamento.aprovado` vai para DLQ), o frontend ficará fazendo polling indefinidamente. **Recomendação:** definir timeout de polling (ex.: 60s) e, ao atingi-lo, mostrar mensagem "Ingresso em processamento — verifique em alguns instantes" com link para "Meus ingressos". Não é bloqueante para o aceite de sprint, mas o Tester deve verificar o comportamento na DLQ (A3.c já cobre o lado servidor; falta o lado cliente).

### Ressalva 3 — Cenário de concorrência: critério US-040.4 exige Postgres real (confirmado, mas atenção no CI)

O teste A2 (`@RepeatedTest(3)`) usa Testcontainers Postgres + UPDATE atômico. O risco R2 (flakiness de CI com Testcontainers RabbitMQ) é real — o CI deve ter Docker disponível. Já mitigado pelo padrão `disabledWithoutDocker=true` da S3, mas o PO registra: **se o CI não subir RabbitMQ, os testes A3/B1 serão pulados em alguns ambientes** — o gate de aceite exige que esses testes rodem no pipeline final (não apenas "verde com skip").

---

## Cenários de concorrência/idempotência — verificação

| Cenário (critério PO) | Coberto na arquitetura? | Coberto nos testes? |
|---|---|---|
| Última vaga PAGO: K simultâneos → 1 reserva, K-1 × 409, sem pagamento criado | ✅ `reservarVaga` UPDATE atômico antes do pagamento (ADR-T07) | ✅ A2 (`@RepeatedTest(3)`, Postgres real) |
| `pagamento.aprovado` reentregue 2× → 1 ingresso | ✅ `processed_events` PK + `ingressos.inscricao_id UNIQUE` | ✅ A3.b (CRÍTICO) |
| `pedido.criado` reentregue 2× → 1 pagamento | ✅ `processed_events` PK + `pagamentos.inscricao_id UNIQUE` | ✅ B1.b (CRÍTICO) |
| Confirmar pagamento 2× → transição idempotente, 1 evento publicado | ✅ Lock pessimista + `confirmar()` retorna bool "transicionou" | ✅ B2.c (CRÍTICO) |
| Rollback na tx local → `afterCommit` não publica | ✅ `TransactionSynchronization.afterCommit` | ✅ A5, B4 (CRÍTICO) |

Todos os cinco cenários críticos estão cobertos em contratos e testes.

---

## Aprovação

[x] **APROVADO COM RESSALVAS**

As três histórias (US-040, US-041, US-060) estão completamente cobertas pela arquitetura. O escopo está limpo — nenhuma feature de S5 vaza. Os cenários de concorrência e idempotência exigidos pelo PO estão refletidos nos contratos, nas ADRs e nos testes especificados.

**Ressalvas (não bloqueantes para início da implementação):**

1. **Checkout com feedback de TTL:** o Frontend deve exibir o tempo restante para o pagamento (30 min) na `CheckoutPage`. Implementar durante o desenvolvimento frontend sem impacto em contratos de API.
2. **Polling com timeout definido:** definir comportamento do frontend quando o polling atingir o tempo limite (saga na DLQ). Sugestão: 60s de timeout + mensagem orientadora. Não altera contratos backend.
3. **CI com Docker obrigatório para os testes críticos:** os testes A3 e B1 (idempotência com RabbitMQ real) não devem ser marcados como "passou" se foram pulados por ausência de Docker. O Tester deve garantir que o pipeline de CI execute com Docker disponível para que o gate de aceite seja válido.

Nenhuma das ressalvas exige revisão de arquitetura ou retrabalho de contratos. O time pode iniciar a implementação.
