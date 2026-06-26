# Sprint 2 — Validação PO da Arquitetura

> Autor: Product Owner. Inputs: `po-planning.md`, `00-sprint-spec.md`, `architecture.md`, `api-contracts.md`, `data-model.md`, `tests-spec.md`.
> Data: 2026-06-26.

---

## Histórias cobertas

- **US-020** ✅ — Criar evento (RASCUNHO, wizard ≤3 etapas, validação de campos, papel antes do banco).
- **US-021** ✅ — Editar / publicar / cancelar (máquina de estados, ownership 403 papel / 404 dono, transições inválidas → 409).
- **US-022** ✅ — Listar/buscar eventos PUBLICADOS (filtros `q`/`tipo`/`de`/`ate`, paginação, estado vazio).
- **US-023** ✅ — Detalhe de evento (RASCUNHO alheio → 404, loading/error, sem botão de inscrição, timezone correto).

---

## Aderência ao escopo

- [x] **Nenhuma feature fora do roadmap introduzida.** Inscrição/ingresso, pagamento, avaliação, check-in, upload de imagem e busca geográfica estão explicitamente excluídos — e o design confirma essa exclusão em todas as camadas (sem endpoint de reserva, sem `RabbitTemplate`, sem botão "Inscrever-se" no front, sem `avaliacoes` mapeada).
- [x] **Wizard ≤ 3 etapas.** Frontend documentado: "dados → data/local → tipo/preço/capacidade/imagem". Critério US-020.1 atendido; Zod guarda dados entre etapas (validação no front não limpa campos ao voltar etapa).
- [x] **RASCUNHO não aparece na lista pública.** `GET /events` filtra `status = PUBLICADO` em query JPQL; `tests-spec.md` exige teste com 1 RASCUNHO + 1 PUBLICADO + 1 CANCELADO → só o PUBLICADO retorna. Critério US-020.2 e US-022.1 cobertos.
- [x] **Bruno (participante) recebe 403 em `POST /events` sem criar dados.** `requirePromotor()` é a **primeira** linha do método antes de qualquer acesso ao banco. Teste asserta `repository.count()` inalterado. Critério US-020.3 coberto.
- [x] **Campos obrigatórios ausentes → erro inline sem perda de dados.** Bean Validation retorna 400 com mensagens por campo; schema Zod espelha no front e mantém estado do formulário. Critério US-020.4 coberto.
- [x] **Evento PAGO com `preco = 0` barrado na borda.** `@AssertTrue isPrecoCoerente()` no record + `CHECK chk_preco_pago` no banco. Critério US-020.5 coberto.
- [x] **Editar RASCUNHO persiste e mantém status.** `PUT /events/{id}` só age em RASCUNHO; `atualizadoEm` atualizado; status continua RASCUNHO. Critério US-021.1 coberto.
- [x] **Publicar: PUBLICADO + `vagas_disponiveis = capacidade`.** Service inicializa o campo; response retorna ambos; teste asserta `vagasDisponiveis == capacidade`. Critério US-021.2 e critério de sucesso §5 cobertos.
- [x] **Ownership: 403 (papel) vs 404 (recurso alheio).** Design é preciso: participante → 403 (decisão pública); promotor B em evento do promotor A → 404 (não enumera existência). Cobre US-021.3 e US-023.2.
- [x] **Publicar evento CANCELADO → erro.** Máquina de estados na entidade: `CANCELADO → publicar` lança `BusinessException(409, "TRANSICAO_INVALIDA")`. Critério US-021.4 coberto.
- [x] **Cancelar PUBLICADO some da lista imediatamente.** Status → CANCELADO; `GET /events` filtra só PUBLICADO; sem cache. Testes de caminho-feliz e de listagem verificam isso. Critérios US-021.5 e US-022.5 cobertos.
- [x] **Paginação sempre presente.** `GET /events` e `GET /events/meus` nunca retornam irrestrito; `size` limitado a 100 no controller; defaults `page=0, size=20`. Critério US-022.3 coberto.
- [x] **Estado vazio ("nenhum evento encontrado").** Definido no frontend (empty state) e no teste (filtro sem match → 200 com `content` vazio). Critério US-022.4 coberto.
- [x] **Detalhe sem botão de inscrição.** Frontend documentado explicitamente: "**Sem** botão de inscrição (Sprint 3)"; teste de detalhe asserta ausência. Critério US-023.4 coberto.
- [x] **Timezone correto.** `architecture.md` identifica o gap (`hibernate.jdbc.time_zone: UTC` ausente no event-service) e o coloca como **item obrigatório no DoD**. O smoke test de timezone (criar evento 14h BRT → detalhe exibe 14h BRT) está na spec de testes. Front envia ISO-8601 com offset; back persiste UTC; detalhe exibe no fuso do usuário. Risco mapeado e mitigado.
- [x] **Filtros combinados corretos.** Query JPQL com `CAST(:q AS string)` cobre `q`/`tipo`/`de`/`ate` combináveis; teste de filtros combinados obrigatório. Critério US-022.2 coberto.
- [x] **Bug Postgres `lower(bytea)` tratado.** Design replica explicitamente a solução da Sprint 1 (`CAST(:q AS string)`) e exige smoke em Postgres real como condição do DoD.
- [x] **`PROMOTOR` sem papel → 403 em qualquer escrita.** Teste cross-papel para todos os endpoints de escrita e para `GET /events/meus`. Critério US-021.6 coberto.

---

## Pontos de atenção

### 1. Editar evento PUBLICADO → 409 (impacto UX em Marina)
O design define que `PUT /events/{id}` em evento PUBLICADO retorna **409 `EVENTO_NAO_EDITAVEL`**. O critério do PO (US-021.1) fala em "editar RASCUNHO", o que é consistente — mas o front precisa desabilitar o botão de edição para eventos PUBLICADOS/CANCELADOS na tela "Meus eventos", ou Marina vai tentar editar e receber um erro 409 sem entender o motivo. Isso não é um bloqueio para aprovação, mas o handoff do frontend deve incluir a instrução de esconder/desabilitar a ação de edição para status != RASCUNHO. Verificar na tela "Meus eventos" durante o aceite.

### 2. Smoke em Postgres real é condição do DoD (não opcional)
O design reconhece corretamente que o H2 mascara dois bugs críticos: (a) `lower(bytea)` e (b) `ddl-auto: validate`. O DoD técnico lista ambos como obrigatórios. O PO reforça: **a entrega não será aceita sem smoke documentado em Postgres** (Testcontainers ou smoke manual registrado em `devops-log.md`). Isso é validação de produto, não só de infraestrutura — um 500 em `GET /events` sem `q` é falha visível para Bruno.

### 3. `vagasDisponiveis` nulo no RASCUNHO exposto ao front
`EventoResponse` retorna `vagasDisponiveis: null` enquanto o evento está em RASCUNHO. Na tela "Meus eventos" de Marina, o front deve tratar `null` de forma legível ("—" ou "disponível após publicar"), não exibir `null`. Não é bloqueio agora (tela de detalhe público nunca mostra RASCUNHO), mas o handoff do frontend deve cobrir esse estado na tela de promotor.

### 4. Detalhe de CANCELADO próprio exposto ao owner
O design define: CANCELADO do **próprio** promotor → 200 em `GET /events/{id}`. Esse comportamento está correto e atende Marina (ela precisa ver os próprios eventos cancelados em "Meus eventos"). Apenas confirmar durante o aceite que o detalhe de evento CANCELADO não exibe botão de "Publicar" (ação inválida pela máquina de estados).

### 5. Sem ressalva de escopo
Nenhum endpoint, DTO ou comportamento introduz feature de inscrição, pagamento, avaliação ou check-in. O campo `vagasDisponiveis` é preparatório (ADR-T07) e não expõe fluxo de reserva. Escopo respeitado.

---

## Aprovação

[x] **APROVADO COM RESSALVAS**  [ ] APROVADO  [ ] REVISAR

### Ressalvas (implementação, não bloqueio de início)

| # | Onde | Critério em risco | Ação esperada antes do aceite |
|---|---|---|---|
| R1 | Frontend — "Meus eventos" | US-021.1 (UX de edição) | Desabilitar/esconder botão de editar para status != RASCUNHO; verificar no aceite |
| R2 | Backend — smoke Postgres | US-022.2/4, US-023.3 | Smoke documentado em `devops-log.md` antes do aceite (Testcontainers ou manual) |
| R3 | Frontend — "Meus eventos" | US-023 UX | Tratar `vagasDisponiveis: null` com texto amigável na tela do promotor |

As ressalvas são de **implementação e handoff**, não de design. A arquitetura proposta está correta e completa para as 4 histórias. **A implementação pode começar.**
