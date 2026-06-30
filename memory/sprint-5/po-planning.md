# Sprint 5 — Planning do PO

## Objetivo (1 frase)

Ao fim deste sprint, o ciclo de vida do evento fecha ponta-a-ponta: Marina valida ingressos por QR na porta, Bruno cancela ou avalia sua participacao, e o dinheiro em escrow e repassado ao promotor (menos 10%) apos o evento realizado — ou reembolsado se o evento for cancelado ou se Bruno cancelar dentro do prazo.

---

## Decisoes de Produto (PO)

### D1 — Elegibilidade de avaliacao (US-024)

**Decisao:** Pode avaliar quem possui **ingresso com status `UTILIZADO`** (fez check-in) **OU** inscricao com status `ATIVA` em evento com status `REALIZADO`.

**Justificativa:** Exigir apenas check-in penaliza eventos grandes onde a validacao de QR e lenta na porta — participantes que ficaram na fila e entraram sem ser "bicados" perderiam o direito de avaliar, prejudicando Marina com dados incompletos. Ja o puro "inscrito ativo" permite avaliacao por quem nao compareceu, o que distorce a reputacao. A combinacao das duas condicoes equilibra: quem fez check-in com certeza foi; quem estava com inscricao ativa em evento realizado provavelmente foi (o ato de cancelar a inscricao ja o removeria da elegibilidade). Um participante que cancelou a inscricao (status `CANCELADA`) **nao e elegivel**. Avaliacao dupla (mesmo usuario, mesmo evento) retorna 409.

### D2 — Politica de reembolso por prazo (US-035)

**Decisao:** Fora do prazo (`prazo_reembolso_dias` do evento), o sistema **bloqueia o cancelamento** — retorna 422 com mensagem clara: "Prazo de cancelamento encerrado. Entre em contato com o organizador." Bruno nao pode cancelar, e nenhum reembolso e iniciado.

**Justificativa:** Cancelar sem reembolso seria uma surpresa negativa de produto: Bruno cancela achando que nada acontece, mas perde o dinheiro sem aviso adequado. Bloquear e mais honesto — a tela mostra o prazo antes de Bruno tentar cancelar, e se ja expirou, ele ve uma mensagem explicativa em vez de perder o valor silenciosamente. Isso tambem protege Marina: ela pode planejar a producao do evento sabendo que cancelamentos tardios nao vao desorganizar a lista de presentes. Se o evento for **cancelado pelo promotor**, o reembolso e sempre integral (sem limitacao de prazo) — essa politica se aplica apenas ao cancelamento voluntario do participante.

---

## Trilha 5A — Financeiro (US-043 e US-042)

**Objetivo:** Fechar a saga financeira — o dinheiro em escrow encontra seu destino: promotor (repasse) ou participante (reembolso).

### Historias selecionadas

| ID | Historia | Criterios de aceite operacionais |
|---|---|---|
| US-043 | Como promotor, quero receber o repasse (menos 10% de taxa) apos o evento ser marcado como REALIZADO | 1. Quando um evento passa para status `REALIZADO` (por job ou endpoint — decisao do Arquiteto), todos os pagamentos com status `CONFIRMADO` desse evento transitam para `REPASSADO` e o campo `valor_repasse` recebe `valor_bruto × 0,90`. <br>2. Marina acessa `/api/payments/me` (ou tela de extrato) e ve o status `REPASSADO` com o valor correto, sem precisar relogar. <br>3. Se a mensagem `evento.finalizado` for reentregue pelo broker (simulacao de retry), o repasse nao e aplicado uma segunda vez — apenas 1 registro de `REPASSADO` por pagamento (idempotencia verificada pelo Admin via log/extrato). <br>4. Um pagamento ja em status diferente de `CONFIRMADO` (ex.: `REEMBOLSADO`) nao e tocado pela mensagem `evento.finalizado`. |
| US-042 | Como participante, quero ser reembolsado se o evento for cancelado pelo promotor (reembolso em massa) ou se eu cancelar dentro da politica (reembolso individual) | 1. **Reembolso em massa:** quando Marina cancela um evento (`POST /api/eventos/{id}/cancelar`), todos os pagamentos `CONFIRMADO` do evento viram `REEMBOLSADO` + registros em `reembolsos(EVENTO_CANCELADO)`; todas as inscricoes `ATIVA` viram `CANCELADA`; ingressos viram `CANCELADO`; vagas sao liberadas (contagem volta a capacidade total). Bruno ve status `REEMBOLSADO` no extrato sem relogar. <br>2. **Reembolso individual (dentro do prazo):** Bruno cancela inscricao em evento pago dentro de `prazo_reembolso_dias` → inscricao `CANCELADA`, ingresso `CANCELADO`, vaga liberada, pagamento `REEMBOLSADO` + registro `reembolsos(CANCELAMENTO_PARTICIPANTE)`. Bruno ve o reembolso no extrato. <br>3. **Fora do prazo:** sistema bloqueia o cancelamento de Bruno com 422 e mensagem explicativa; nenhum reembolso e iniciado; inscricao permanece `ATIVA`. (Ver Decisao D2.) <br>4. Se `evento.cancelado` for reentregue, reembolso em massa aplicado apenas 1× por pagamento (idempotencia). <br>5. Admin consegue listar pagamentos com status `REEMBOLSADO` e `REPASSADO` para auditoria. |

### Cenarios de concorrencia — Trilha 5A

| Cenario | Comportamento esperado |
|---|---|
| `evento.finalizado` reentregue 2× | Apenas 1 transicao `CONFIRMADO → REPASSADO` por pagamento; segunda mensagem e no-op (processed_events). |
| `evento.cancelado` reentregue 2× | Idem: 1 reembolso por pagamento; ticket-service 1 cancelamento por inscricao. |
| Repasse e reembolso no mesmo pagamento (corrida rara) | `UPDATE ... WHERE status='CONFIRMADO'` — apenas um vence; o outro e no-op. Nenhum pagamento fica em estado invalido. |

---

## Trilha 5B — Experiencia (US-034, US-035, US-024, US-025)

**Objetivo:** Completar o ciclo do participante e do promotor no dia do evento — da porta (check-in) ao pos-evento (cancelamento, avaliacao, reputacao).

### Historias selecionadas

| ID | Historia | Criterios de aceite operacionais |
|---|---|---|
| US-034 | Como promotor, quero validar o ingresso (check-in por QR) na porta do evento | 1. Marina (promotora dona do evento) escaneia o QR de um ingresso `ATIVO` do seu evento: recebe 200, ingresso muda para `UTILIZADO`, registro criado em `checkins`. <br>2. Se Marina escaneia o mesmo QR pela segunda vez, recebe 409 com mensagem "Ingresso ja utilizado" — nenhuma alteracao no banco. <br>3. Se Marina tenta validar ingresso de **outro evento** (que nao e dela), recebe 403. <br>4. Se o QR nao existe ou pertence a inscricao cancelada, recebe 404. <br>5. Se um participante (role PARTICIPANTE) chama o endpoint de check-in, recebe 403. <br>6. Dois dispositivos de Marina tentam fazer check-in do mesmo ingresso ao mesmo tempo: exatamente 1 sucesso (200) e 1 erro (409) — sem duplicata em `checkins`. |
| US-035 | Como participante, quero cancelar minha inscricao conforme a politica | 1. Bruno cancela inscricao em evento **gratuito** antes ou depois do prazo: inscricao `CANCELADA`, ingresso `CANCELADO`, vaga liberada (vagas_disponiveis +1, nunca acima da capacidade). <br>2. Bruno cancela inscricao em evento **pago dentro do prazo**: idem acima + pagamento `REEMBOLSADO` + registro `reembolsos(CANCELAMENTO_PARTICIPANTE)`. Bruno ve reembolso no extrato. <br>3. Bruno tenta cancelar inscricao paga **fora do prazo**: recebe 422 com "Prazo de cancelamento encerrado." Inscricao permanece `ATIVA`. (Ver Decisao D2.) <br>4. Bruno nao pode cancelar a inscricao de outro participante (retorna 403). <br>5. Dois cancelamentos simultaneos da mesma inscricao: exatamente 1 sucesso; o segundo retorna 404 ou 409 (inscricao ja cancelada). |
| US-024 | Como participante, quero avaliar um evento que participei (nota de 1 a 5 mais comentario) | 1. Bruno (elegivel: ingresso `UTILIZADO` **ou** inscricao `ATIVA` em evento `REALIZADO`) acessa `POST /api/eventos/{id}/avaliacoes` com `{nota: 4, comentario: "..."}` e recebe 201. <br>2. Se Bruno tentar avaliar o mesmo evento uma segunda vez, recebe 409. <br>3. Se Bruno nao e elegivel (inscricao cancelada, evento nao realizado, ou sem qualquer vinculo), recebe 403 com mensagem de inelegibilidade. <br>4. Nota fora do intervalo 1-5 retorna 400 (validacao). <br>5. Um admin ou promotor nao participante nao pode avaliar (403). |
| US-025 | Como promotor, quero ver a reputacao (media de avaliacoes) do meu evento | 1. O endpoint `GET /api/eventos/{id}` passa a incluir `{"reputacao": {"media": 4.2, "total": 37}}` quando ha avaliacoes; `{"reputacao": {"media": null, "total": 0}}` quando nao ha. <br>2. Marina ve a reputacao atualizada imediatamente apos nova avaliacao (sem cache obsoleto). <br>3. Qualquer usuario autenticado pode ler a reputacao no detalhe do evento (nao e dado restrito). |

### Cenarios de concorrencia — Trilha 5B

| Cenario | Comportamento esperado |
|---|---|
| Duplo check-in simultaneo do mesmo QR | `UNIQUE(ingresso_id)` em `checkins` + `UPDATE WHERE status='ATIVO'` → exatamente 1 sucesso (200), 1 erro (409). |
| Cancelamento e check-in simultaneos do mesmo ingresso | `UPDATE ... WHERE status='ATIVO'` serializa: quem chegar primeiro vence; o segundo recebe 409 (check-in) ou 404/409 (cancelamento). |
| Dupla avaliacao simultanea | `UNIQUE(evento_id, usuario_id)` em `avaliacoes` → 1 sucesso (201), 1 erro (409). |
| Dois cancelamentos simultaneos da mesma inscricao | Segundo cancelamento encontra inscricao ja `CANCELADA` → 409. Vaga liberada apenas 1×. |

---

## Trilha 5C — Qualidade (US-061, US-062*, US-063*)

**Objetivo:** Validar o sistema sob carga real e encerrar dividas tecnicas antes da banca.

> US-062 e US-063 sao **propostos** — aguardam confirmacao no gate do dono antes de entrar no sprint.

### Historias selecionadas

| ID | Historia | Criterios de aceite operacionais |
|---|---|---|
| US-061 | Como time, quero testes de carga no abre-vendas (concorrencia de inscricao) | 1. Dado um evento com `M` vagas, `N` usuarios (N > M) disparando inscricao simultaneamente: exatamente `M` respostas com sucesso (200/201), `N − M` respostas com 409 (esgotado), `vagas_disponiveis = 0` ao final. **Zero overbooking** (nenhum ingresso emitido alem de M). <br>2. Relatorio do teste inclui: taxa de throughput (requisicoes/s), latencia P50/P95/P99, e confirmacao do invariante acima. <br>3. Teste roda contra Postgres real (Testcontainers ou stack Docker) — nao H2 (row lock nao e reproduzido no H2). |
| US-062 *(proposto — aguarda gate)* | Como time, quero observabilidade basica nos servicos (health/readiness, metricas, logs estruturados) | 1. `GET /actuator/health` retorna 200 com status UP em todos os servicos em execucao normal. <br>2. `GET /actuator/readiness` retorna DOWN quando o banco ou broker estiver indisponivel. <br>3. Logs em producao sao JSON estruturado (nivel, timestamp, servico, traceId). <br>4. Admin consegue identificar em qual servico ocorreu um erro apenas pelo log, sem precisar de acesso ao codigo. |
| US-063 *(proposto — aguarda gate)* | Como time, quero fechar dividas tecnicas pre-banca (whitelist do gateway, seed admin env-driven, follow-ups TECH-S3) | 1. **ADR-T03:** a whitelist do gateway usa match exato (nao `startsWith`); chamada para `/api/auth/register-extended` (prefixo valido, path invalido) retorna 401/404, nao passa pela rota aberta. <br>2. **ADR-T05:** a credencial do admin seed e controlada por variaveis de ambiente (`ADMIN_EMAIL`, `ADMIN_PASSWORD_HASH`); o hash nao aparece em texto claro no codigo versionado. <br>3. **TECH-S3-01/02/03/04:** as quatro dividas de code review documentadas no backlog foram endereçadas (reservar/liberar vaga, dedupe MeusIngressos, refetch vagasDisponiveis no frontend, INTERNAL_TOKEN em producao). |

### Cenario de concorrencia principal — Trilha 5C

| Cenario | Comportamento esperado |
|---|---|
| **Abre-vendas sob carga (US-061):** N usuarios × M vagas | Exatamente M ingressos emitidos, N−M respostas 409, `vagas_disponiveis = 0`, nenhum registro duplicado. Invariante verificado por consulta direta ao banco apos o teste. |

---

## Atores exercitados

- **Bruno (participante, 24):**
  - Cancela inscricao em evento pago dentro do prazo e ve reembolso no extrato (US-035 + US-042).
  - Tenta cancelar fora do prazo e recebe mensagem clara de bloqueio (US-035, Decisao D2).
  - Avalia evento que participou com nota e comentario; nao consegue avaliar duas vezes (US-024).
  - Tenta fazer check-in no lugar de Marina e recebe 403 (US-034, papel incorreto).

- **Marina (promotora, 35):**
  - Valida ingressos por QR na porta: 1° leitura ok, 2° leitura 409 (US-034).
  - Cancela evento e ve todos os participantes reembolsados automaticamente (US-042).
  - Marca evento como realizado e ve o repasse creditado no extrato, com o valor correto (US-043).
  - Abre a pagina do evento e ve a reputacao (media + total de avaliacoes) atualizada (US-025).

- **Admin (operacao):**
  - Consulta extrato de pagamentos e identifica status `REPASSADO`, `REEMBOLSADO`, `CONFIRMADO` para auditoria (US-042, US-043).
  - Em US-063 (se aprovado): confirma que credencial admin nao esta em texto claro no repo e que a whitelist do gateway nao aceita prefixos espurios.

---

## Recomendacao de faseamento

A spec mestre recomenda **5A → 5B → 5C** e o PO endossa com a seguinte leitura de produto:

1. **5A primeiro (financeiro):** A dor mais critica e o dinheiro preso em escrow. Um ingresso de R$150 em estado `CONFIRMADO` indefinidamente e o maior risco de reputacao da plataforma. Alem disso, 5A depende diretamente do Sprint 4 mergeado (escrow + AMQP), entao e o gate natural: se o S4 ainda nao estiver integrado, 5A trava; e melhor descobrir isso cedo. Caminho-feliz aqui = Marina ve o repasse; Bruno ve o reembolso. Sao os cenarios que o dono do produto vai querer ver antes de qualquer demo.

2. **5B segundo (experiencia):** Com a saga financeira fechada, o ciclo de vida do ingresso se completa. US-034 (check-in) e a historia mais visivel para uma demo presencial — Marina lendo QR na porta. US-035 (cancelamento) precisa estar antes de US-024 (avaliacao), pois a elegibilidade de avaliacao depende do status da inscricao. A trilha 5B e mais segura de desenvolver em paralelo dentro do time, pois US-034 e US-035 tocam o ticket-service e US-024/025 tocam o event-service.

3. **5C por ultimo (qualidade):** Testes de carga (US-061) pressupoe o fluxo de inscricao estavel — e um validator, nao um construtor. US-062 e US-063 sao dividas que nao adicionam valor de produto visivel ao usuario, mas reduzem risco operacional e de banca. Colocados no final porque nao desbloqueiam outras historias e podem ser cortados se o tempo acabar sem impactar a experiencia central.

**Risco de corte:** se o sprint nao couber em 2 semanas, o PO recomenda cortar **US-062** (observabilidade) primeiro, depois **US-063** (hardening tecnico). US-061 (carga) nao deve ser cortado — e o unico criterio de qualidade com criterio de aceite de produto (zero overbooking).

---

## Riscos de produto

| Risco | Probabilidade | Impacto | Mitigacao |
|---|---|---|---|
| R0 — Sprint 4 nao mergeado quando 5A comeca | Alta | Bloqueia toda a 5A | Gate explícito: nao iniciar 5A sem S4 mergeado e `pagamentos.status=CONFIRMADO` funcionando. |
| R1 — Transicao REALIZADO/CANCELADO indefinida (job vs. endpoint) | Media | Desbloqueia ou bloqueia US-043/042 desde o inicio | Arquiteto decide **antes** de iniciar o backend de 5A. Sem isso, 5A nao tem gatilho. |
| R2 — Reembolso em massa inconsistente (payment + ticket consomem `evento.cancelado`) | Media | Overbooking de reembolso ou vagas nao liberadas | `processed_events` em ambos os consumidores; gate de teste: verificar `vagas_disponiveis = capacidade` apos cancelamento em massa. |
| R3 — Elegibilidade de avaliacao gera reclamacao de promotor | Baixa | Reputacao distorcida (avaliacao de nao-participantes) | Decisao D1 e clara: inscricao cancelada nao e elegivel. Backend valida, nao confia no frontend. |
| R4 — Check-in sem controle de ownership | Media | Promotor A valida ingressos do evento de Marina | Autorizacao double-check: papel PROMOTOR **e** `evento.promotorId == usuarioId`. |
| R5 — Prazo de reembolso nao exibido na tela antes de Bruno tentar cancelar | Alta (UX) | Bruno cancela sem saber que esta fora do prazo, frustracao | Frontend exibe `prazo_reembolso_dias` e a data-limite calculada antes do botao de cancelar; botao desabilitado ou com aviso se fora do prazo. |

---

## Fora deste sprint (intencional)

- **US-060** — ja e do Sprint 4 (fundacao AMQP + `processed_events`). Nao sera tocado aqui.
- Gateway de pagamento real (integracao com Stripe, PagSeguro etc.) — fora do escopo academico.
- mTLS entre servicos (ADR-T08 registrado como divida; over-engineering para o MVP).
- Reputacao de **promotor** agregada entre eventos (apenas por evento neste sprint).
- Notificacoes por e-mail de repasse ou reembolso (infra de e-mail existe no user-service, mas nao foi solicitada pelo dono).
- Reembolso automatico por prazo vencido (job que detecta prazo expirado e cancela sem acao do participante) — fora do escopo.
