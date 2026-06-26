# Sprint 3 — Validação PO da Arquitetura (Inscrição & Ingresso QR · gratuito)

> PO: encarnando Bruno (participante), Marina (promotora como participante) e Admin.
> Inputs lidos: `po-planning.md`, `00-sprint-spec.md`, `architecture.md`, `api-contracts.md`, `data-model.md`, `tests-spec.md`.

---

## Histórias cobertas

- **US-030** ✅ — Inscrição em evento gratuito publicado → 201 com ingresso e `codigoUnico`; bloqueio explícito de evento PAGO (422 `EVENTO_PAGO_NAO_SUPORTADO`) e não-publicado (422 `EVENTO_NAO_PUBLICADO`); 503 tipado quando event-service cai. Cobre os 5 critérios de aceite.
- **US-031** ✅ — Dupla inscrição: `UNIQUE(usuario_id, evento_id)` + captura de `DataIntegrityViolationException` → 409 `JA_INSCRITO` (critério 2). Capacidade: decremento atômico `WHERE vagas_disponiveis > 0` com `rowsAffected` → 409 `EVENTO_ESGOTADO` (critério 3). Concorrência última vaga: A4 (K=50 threads em Postgres real via Testcontainers) → exatamente 1 sucesso, K-1×409, `vagas_disponiveis=0` nunca negativo (critério 4). Dupla inscrição paralela: B4 → exatamente 1 sucesso (critério 5). Event-service fora → 503, nada debitado (critério 6). Todos os 6 critérios cobertos.
- **US-032** ✅ — `codigo_unico` = UUID v4, `UNIQUE(codigo_unico)` no banco; ingresso criado na mesma transação da inscrição (rollback atômico); `UNIQUE(inscricao_id)` impede dupla emissão (critério 4); frontend renderiza QR a partir da string (sem imagem no backend). Todos os 5 critérios cobertos.
- **US-033** ✅ — `GET /tickets/me` (meus ingressos com `codigoUnico` + `statusIngresso` + `eventoId`; 200 `[]` se vazio); `GET /tickets/inscricoes/me` (paginado, `inscritoEm,desc`; `size` capado em 100); estado vazio amigável previsto; sem N+1 (join `ingressos⨝inscricoes`). Todos os 5 critérios cobertos.

---

## Aderência ao escopo

- [x] **Sem caminho pago / escrow / AMQP:** inscrição em evento PAGO retorna 422 `EVENTO_PAGO_NAO_SUPORTADO` (bloqueio explícito). Nenhuma referência a `RabbitTemplate`, saga AMQP ou escrow. `processed_events` explicitamente fora (Sprint 4). ✅
- [x] **Sem check-in:** tabela `checkins` existe no V1 mas não é mapeada em `@Entity` nesta sprint. Nenhum endpoint de validação de QR na porta. ✅ (Sprint 5)
- [x] **Sem cancelamento de inscrição:** enum `CANCELADA`/`CANCELADO` existe no código para compatibilidade com o schema, mas nenhum endpoint ou lógica o produz agora. ✅ (Sprint 5)
- [x] **Vagas reais no detalhe do evento:** `vagas_disponiveis` já existe no `event_db` (V2, Sprint 2); o detalhe do evento que o front já exibe pode mostrar o campo. A arquitetura não adiciona endpoint novo para isso (campo já disponível no `GET /api/events/{id}` da Sprint 2). ✅
- [x] **"Meus ingressos" + histórico:** dois endpoints distintos (`GET /tickets/me` e `GET /tickets/inscricoes/me`) cobrem os dois casos de uso de US-033. ✅
- [x] **2ª inscrição → 409 `JA_INSCRITO`:** mensagem semântica explícita no contrato; mapeada no `GlobalExceptionHandler` (`DataIntegrityViolationException` → 409). ✅
- [x] **(N+1)ª inscrição → 409 `EVENTO_ESGOTADO`:** mensagem semântica distinta de `JA_INSCRITO`. O front pode diferenciar e exibir textos diferentes para o usuário. ✅
- [x] **Concorrência última vaga → exatamente 1 sucesso:** coberto em A4 (50 threads, Postgres real, repetido 5–10x). Gate inegociável do DoD. ✅
- [x] **Ingresso único por inscrição:** `UNIQUE(inscricao_id)` + mesma transação; B6 testa rollback atômico. ✅
- [x] **QR renderizável no mobile:** backend devolve string `codigoUnico` (UUID v4, 36 chars), frontend renderiza com `qrcode.react`. Critério "leitura visível em tela de 5"" é responsabilidade do front — o contrato entrega o `codigoUnico` para isso. ✅
- [x] **Saga transparente ao usuário:** do ponto de vista de Bruno, o POST de inscrição retorna 201 (sucesso) ou erro tipado (409/422/503). A compensação (`liberar-vaga`) é interna, invisível; o usuário nunca vê um estado "parcialmente inscrito". ✅

---

## Pontos de atenção

### 1. `GET /tickets/me` não traz nome/data/local do evento — Bruno pode ficar confuso
O endpoint devolve `eventoId` mas não o nome, data ou local do evento. A decisão de arquitetura (evitar fan-out N+1 cross-service) é tecnicamente sólida, mas **Bruno, na tela "Meus ingressos", verá inicialmente apenas o ID do evento**. A composição com o event-service fica no frontend. É aceitável se o front resolver de forma não-perceptível ao usuário (busca em cache ou batch). O Arquiteto e o Frontend precisam garantir que a tela não renderize "eventoId: 42" para o usuário — o critério US-033.1 exige "nome do evento, data, local". **Isso é responsabilidade explícita do handoff front — não é falha de escopo, mas é um risco de entrega se não houver coordenação.**

Classificação: **atenção de coordenação** (não bloqueia aprovação; o Tester e o Frontend precisam verificar que US-033.1 está atendido na integração real).

### 2. Mensagens de erro para Bruno — diferenciação clara entre JA_INSCRITO e EVENTO_ESGOTADO
O contrato usa códigos semânticos (`JA_INSCRITO`, `EVENTO_ESGOTADO`) em `message`. O front recebe o código e traduz para pt-BR. Isso é o padrão correto para internacionalização. Os testes de front (D1) verificam que as mensagens corretas aparecem. ✅ — o risco de "mensagem genérica de erro" está mitigado pelo design de `ErrorResponse` tipado.

### 3. QR renderizável em mobile (5")
UUID v4 tem 36 caracteres. A renderização de QR de uma string curta (36 chars) gera um QR de baixa densidade — leitura fácil em mobile. A lib `qrcode.react` recomendada é bem estabelecida. O critério US-032.3 deve ser verificado manualmente com um QR renderizado em tela de 5" antes do aceite final (teste de usabilidade de Bruno). **Não é problema técnico; é gate de aceite humano no final da sprint.**

### 4. Compensação não perfeitamente idempotente por reserva individual
O Arquiteto registrou explicitamente: `liberar-vaga` não tem "token de reserva", então chamar duas vezes poderia devolver 2 vagas. O teto `vagas < capacidade` mitiga. Para o escopo desta sprint (fluxo síncrono sem retry automático de `liberar`), o risco é controlado. A reconciliação fina fica para Sprint 4 (AMQP). **Aceitável neste sprint; registrado.**

### 5. `reservar-vaga` nunca exposto publicamente — confirmado e testado
O wildcard `/api/events/**` do gateway poderia ter capturado os internos. A decisão de usar prefixo `/internal/...` (sem rota no gateway) + `X-Internal-Token` é correta. O teste C (roteamento) é gate de segurança obrigatório. **Este era um risco de produto crítico (DoS de vagas) — a solução é adequada.**

### 6. H2 não conta para o gate de concorrência
O Arquiteto sinalizou explicitamente: o teste de concorrência (A4, A5, B4, B5) exige Postgres real via Testcontainers. Testes em H2 podem dar falso verde. Se o CI da sprint não tiver Testcontainers configurado, o DoD de concorrência NÃO está cumprido — isso é uma dor de produto (overbooking silencioso que só aparece em produção). **O PO exige que o resultado de `./mvnw verify` inclua os testes de Postgres real antes do merge.**

### 7. Critério US-031.6 — event-service fora → 503, nada debitado
O passo 1 (validação) e o passo 2 (reservar-vaga) ambos podem falhar por timeout. O contrato está correto: qualquer falha antes do passo 3 não cria inscrição nem debita vaga. O caso ambíguo (timeout de leitura no passo 2) resulta em possível vaga "presa" (conservador: não causa overbooking). A documentação e o log estruturado são suficientes para o escopo acadêmico. ✅

---

## Aprovação

[x] **APROVADO COM RESSALVAS**

**Ressalva 1 (coordenação obrigatória):** US-033.1 exige que "Meus ingressos" exiba nome/data/local do evento. O endpoint `GET /tickets/me` devolve apenas `eventoId`. O Frontend precisa fazer a composição com o event-service de forma não-perceptível ao usuário. O Tester deve validar a tela real (não só o endpoint isolado) antes do aceite final.

**Ressalva 2 (gate humano):** US-032.3 exige que o QR seja legível em mobile 5". O Tester e o PO devem verificar o QR renderizado em tela real ou emulador antes do aceite.

**Ressalva 3 (gate de CI obrigatório):** Os testes de concorrência (A4, A5, B4, B5) DEVEM rodar com Postgres real (Testcontainers) no `./mvnw verify`. Se o pipeline CI não tiver essa infra, o merge é bloqueado. Falso verde em H2 não cumpre o DoD.

---

> As três ressalvas são **gates de aceite** (serão verificadas na `po-acceptance.md`), não pedidos de revisão de arquitetura. A solução técnica proposta está correta, coerente com o backlog e dentro do escopo. A concorrência está devidamente coberta. **Aprovado para implementação.**
