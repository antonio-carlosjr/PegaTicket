# Sprint 4 — Planning do PO

## Objetivo (1 frase)

"Ao fim deste sprint, Bruno se inscreve num evento PAGO, paga via gateway simulado, e so entao recebe o ingresso com QR — emitido por uma saga assincrona (RabbitMQ) idempotente e a prova de concorrencia, com o dinheiro retido em escrow ate o repasse futuro."

---

## Historias selecionadas

| ID | Historia | Criterios de aceite operacionais |
|---|---|---|
| **US-060** | Como time, quero consumidores RabbitMQ idempotentes (`processed_events`) ligados, para que mensagens duplicadas (at-least-once) nao gerem efeitos duplos em nenhum servico. | 1. O time verifica no banco (`processed_events`) que a segunda entrega de qualquer mensagem com o mesmo `event_id` e ignorada — nenhum registro duplicado criado em `pagamentos` ou `ingressos`. 2. O CI verde (`./mvnw verify`) com Testcontainers RabbitMQ + Postgres confirma que `@RabbitListener` e exercitado em ambiente real, nao apenas com mocks. 3. Produtor nao emite evento se a transacao local sofreu rollback — verificado por teste de unidade forcando rollback antes do `afterCommit`. |
| **US-040** | Como participante (Bruno), quero pagar um evento pago via gateway simulado, para que o dinheiro fique retido em escrow ate o repasse posterior. | 1. Bruno se inscreve num evento PAGO → a tela exibe "Pagamento pendente" (status `PENDENTE_PAGAMENTO`) sem emitir ingresso. 2. Bruno aciona "Pagar" (1 toque na tela de checkout) → o sistema cria `pagamentos.status=CONFIRMADO` com `valor_bruto`, `valor_taxa=10%` e `valor_repasse=valor_bruto−valor_taxa` computado e **nao liberado**. 3. **Cenario de escrow (Admin audita):** Admin consulta a listagem de pagamentos e ve o pagamento no estado `CONFIRMADO` (retido), com os tres campos de valor corretos; nao ha nenhum movimento de repasse registrado. 4. **Cenario de concorrencia — ultima vaga em evento PAGO:** K participantes tentam se inscrever simultaneamente na ultima vaga → exatamente 1 reserva e criada (`vagas_disponiveis=0`) e K−1 recebem HTTP 409 ("vagas esgotadas"); nenhuma das K−1 chega a criar pagamento. Verificado com Testcontainers Postgres. 5. Confirmacao de pagamento reenviada 2× (mesmo `inscricaoId`) → transicao `PENDENTE→CONFIRMADO` e idempotente (no-op se ja `CONFIRMADO`); apenas 1 evento `pagamento.aprovado` e publicado. |
| **US-041** | Como sistema, quero emitir o ingresso somente apos `pagamento.aprovado` (saga assincrona de inscricao paga), para que nenhum ingresso seja gerado sem pagamento confirmado. | 1. Bruno conclui o pagamento → o ingresso com QR aparece em "Meus ingressos" dentro de instantes (saga assincrona); antes disso, a tela exibe "aguardando confirmacao de pagamento". 2. Sem `pagamento.aprovado`: nenhum ingresso e criado — verificado injetando inscricao `PENDENTE_PAGAMENTO` no banco sem publicar o evento; "Meus ingressos" nao exibe ingresso. 3. **Cenario de idempotencia de ingresso:** `pagamento.aprovado` reentregue (simulado com 2 entregas do mesmo `event_id`) → somente 1 ingresso emitido (constraint `UNIQUE(inscricao_id)` + `processed_events` bloqueiam o segundo). 4. Fluxo GRATUITO da Sprint 3 continua intacto: inscricao em evento GRATUITO gera ingresso imediatamente, sem passar por pagamento. 5. **Marina (promotora) ve o inscrito como ATIVO** na listagem do evento somente apos o ingresso ser emitido — antes aparece como `PENDENTE_PAGAMENTO`, nao como participante confirmado. |

---

## Atores exercitados

- **Bruno (participante, celular, com pressa):** percorre o caminho pago completo — inscreve-se, ve "pagamento pendente", aciona "Pagar" com 1 toque, aguarda o feedback "ingresso emitido", e abre o QR em "Meus ingressos". Tambem e o ator dos cenarios negativos: tenta a ultima vaga disputada com outros K participantes (recebe 409 claro) e experimenta o gateway simulado devolvendo erro (deve ver mensagem amigavel, nao stack trace).

- **Marina (promotora):** abre o painel do seu evento PAGO e ve a contagem de inscritos separada por status (`PENDENTE_PAGAMENTO` vs `ATIVA`). Confirma que so os inscritos com ingresso emitido (ATIVO) aparecem como participantes confirmados. Nao e bloqueada em nenhum passo — evento GRATUITO continua funcionando normalmente.

- **Admin (operacao/auditoria):** acessa a listagem de pagamentos e verifica que cada pagamento CONFIRMADO exibe `valor_bruto`, `valor_taxa` (10%) e `valor_repasse` corretos, e que nenhum repasse foi executado (escrow puro). Consegue rastrear, para cada pagamento, o `inscricaoId` e o `usuarioId` correspondentes.

---

## Riscos de produto

| # | Risco | Impacto | Mitigacao |
|---|---|---|---|
| R1 | **Vaga presa em pagamento abandonado:** Bruno inicia inscricao, nao paga, e a vaga fica ocupada para sempre — prejudica a capacidade real do evento e Marina. | Alto (confianca do promotor, overbooking inverso). | Arquiteto decide: TTL com job que expira `PENDENTE_PAGAMENTO` apos N minutos (libera vaga) **ou** gap documentado como divida; o PO nao bloqueia o sprint, mas exige que a politica esteja documentada. |
| R2 | **Testcontainers RabbitMQ + Postgres no CI:** testes de consumidor exigem broker real — risco de flakiness e tempo de build. | Medio (CI instavel bloqueia merge). | Padrao "skip local sem Docker, roda no CI" ja adotado na Sprint 3 com Testcontainers Postgres. Reutilizar o padrao. |
| R3 | **Feedback assincrono para Bruno:** saga pode demorar; Bruno nao pode ficar numa tela travada esperando. | Medio (UX — Bruno no celular com pressa). | Frontend exibe estado intermediario ("aguardando pagamento") e atualiza por polling ou websocket simples; aceite requer que o ingresso apareca sem recarregar a pagina manualmente. |
| R4 | **Sprint mais complexo ate aqui (2 servicos + saga + frontend):** risco de nao entregar tudo. | Medio. | Reembolso/repasse fora do escopo; caminho-feliz ponta-a-ponta primeiro; payment-service espelha o user-service como gabarito de estrutura. |

---

## Fora deste sprint (intencional)

- **US-042 — Reembolso:** o escrow aqui so **retem** o dinheiro (`pagamentos.status=CONFIRMADO`); nenhum estorno ou reembolso e executado. → Sprint 5.
- **US-043 — Repasse ao promotor (menos 10%):** `valor_repasse` e **computado** neste sprint mas **nao liberado**. A logica de transferencia fica para Sprint 5.
- **US-034 — Check-in por QR na porta:** Sprint 5.
- **US-035 — Cancelamento de inscricao:** Sprint 5 (depende de reembolso).
- **US-024/025 — Avaliacoes de evento:** Sprint 5.
- **US-061 / RNF09 — Testes de carga e observabilidade:** Sprint posterior.
- **Gateway de pagamento real:** somente `SIMULADO` neste sprint; integracao com Stripe/PagSeguro e escopo futuro fora do roadmap academico.
- **mTLS inter-servico (ADR-T08):** divida documentada; reusamos `X-Internal-Token` sem mTLS no MVP.
