# Sprint 2 — Handoff para o Tester

> Autor: Senior Frontend Engineer. Data: 2026-06-26.
> Frontend pronto para integração e testes manuais/E2E.

---

## Pré-requisitos

1. Backend do event-service rodando (via Docker Compose ou `./mvnw spring-boot:run`)
2. Gateway rodando na porta 8080
3. Frontend: `cd frontend && npm run dev` → http://localhost:5173
4. Usuário PROMOTOR verificado disponível (use o admin para aprovar)
5. Usuário PARTICIPANTE disponível

---

## Telas implementadas e fluxos de teste

### 1. Lista de Eventos — `/eventos`
**Quem acessa:** qualquer usuário autenticado (PARTICIPANTE, PROMOTOR, ADMIN).
**Nav:** item "Eventos" no header.

**Fluxos a testar:**
- [ ] Acesso não autenticado → redireciona para `/login`
- [ ] Lista eventos PUBLICADOS (não deve aparecer RASCUNHO nem CANCELADO)
- [ ] Estado vazio: nenhum evento publicado → exibe "Nenhum evento encontrado"
- [ ] Busca textual (`q`): digitar parte do título ou local → lista filtra
- [ ] Filtro por tipo: "Gratuito" / "Pago"
- [ ] Filtro por data "A partir de" e "Até"
- [ ] Combinação de filtros (q + tipo + data)
- [ ] Limpar filtros restaura lista completa
- [ ] Paginação (se houver > 20 eventos): botões Anterior/Próxima funcionam
- [ ] Card de evento exibe: título, data de início, local, preço (ou "Gratuito"), badge de tipo
- [ ] Botão "Ver detalhes" navega para `/eventos/:id`

### 2. Detalhe do Evento — `/eventos/:id`
**Quem acessa:** qualquer autenticado.

**Fluxos a testar:**
- [ ] Evento PUBLICADO → exibe título, descrição, data início, data fim, local, tipo, preço, capacidade, vagas
- [ ] Datas exibidas no fuso do navegador (pt-BR) com "BRT" ou "-03:00" na abreviação
- [ ] Preço PAGO → valor em R$ com prazo de reembolso visível
- [ ] Evento GRATUITO → exibe "Gratuito"
- [ ] Imagem renderizada se URL presente; placeholder se ausente
- [ ] Evento CANCELADO (próprio promotor) → exibe banner "Este evento foi cancelado."
- [ ] RASCUNHO alheio → API retorna 404 → mensagem "Evento nao encontrado ou nao disponivel."
- [ ] **Sem botão de inscrição** (verificar ausência de botão "Inscrever-se")
- [ ] Botão "Voltar" navega para `/eventos`

### 3. Meus Eventos — `/meus-eventos`
**Quem acessa:** apenas PROMOTOR.
**Nav:** item "Meus eventos" no header (visível apenas para PROMOTOR).

**Fluxos a testar:**
- [ ] PARTICIPANTE tenta acessar `/meus-eventos` → redireciona para `/`
- [ ] PROMOTOR pendente vê a tela normalmente (pode criar mas não publicar via frontend sem verificação)
- [ ] Estado vazio: "Voce ainda nao criou nenhum evento" com CTA "Criar evento"
- [ ] Lista exibe eventos em qualquer status: RASCUNHO, PUBLICADO, CANCELADO, REALIZADO
- [ ] Badge de status colorido:
  - RASCUNHO → amarelo/warning
  - PUBLICADO → verde/success
  - CANCELADO → vermelho/destructive
  - REALIZADO → cinza/secondary
- [ ] **R1: botão "Editar" aparece APENAS para eventos RASCUNHO**
  - PUBLICADO → sem botão Editar
  - CANCELADO → sem botão Editar
- [ ] **R3: campo vagas com texto amigável:**
  - RASCUNHO → "Disponível após publicar" (nunca "null")
  - PUBLICADO → "—" (exato do detalhe, pois resumo não tem vagasDisponiveis)
- [ ] Botão "Publicar": RASCUNHO → PUBLICADO (toast sucesso; lista recarrega)
- [ ] Botão "Publicar" em PUBLICADO → não aparece (já publicado)
- [ ] Botão "Cancelar": RASCUNHO/PUBLICADO → dialog de confirmação → CANCELADO (toast; lista recarrega)
- [ ] Botão "Cancelar" em CANCELADO → não aparece
- [ ] 409 EVENTO_JA_PUBLICADO → toast com mensagem específica
- [ ] 409 TRANSICAO_INVALIDA → toast com mensagem específica
- [ ] Botão "Ver" → navega para `/eventos/:id`
- [ ] Botão "Criar evento" → navega para `/eventos/novo`

### 4. Criar Evento — `/eventos/novo`
**Quem acessa:** apenas PROMOTOR.
**Nav:** link "Criar evento" com ícone (+) no header.

**Fluxos a testar:**

**Wizard:**
- [ ] Etapa 1 — Dados gerais: título (obrigatório), descrição (opcional)
- [ ] Avançar sem título → erro inline "Titulo e obrigatorio" — sem navegar
- [ ] Avançar com título → Etapa 2
- [ ] **Voltar da Etapa 2 → Etapa 1 mantém o título digitado** (dados não perdidos)
- [ ] Etapa 2 — Data e local: data início, data fim, local (todos obrigatórios)
- [ ] dataFim < dataInicio → erro "Data de fim deve ser igual ou posterior a data de inicio"
- [ ] Etapa 3 — Tipo/preço/capacidade/imagem:
  - Tipo GRATUITO: campos preço e prazo não aparecem
  - Tipo PAGO: campos preço (obrigatório > 0) e prazo (obrigatório >= 0) aparecem
  - PAGO com preço 0 → erro "Evento PAGO exige preco maior que zero"
  - Capacidade 0 → erro "Capacidade deve ser maior que zero"
  - URL imagem: opcional

**Submissão:**
- [ ] Evento válido → POST /api/events → 201 → toast "Evento criado como rascunho!" → redireciona para `/meus-eventos`
- [ ] Evento aparece em Meus Eventos com status RASCUNHO
- [ ] 400 do backend com campo: erro mapeado no campo correspondente
- [ ] 403 não-PROMOTOR: protegido pela rota — nunca chega no formulário

### 5. Editar Evento — `/eventos/:id/editar`
**Quem acessa:** apenas PROMOTOR owner do evento em RASCUNHO.

**Fluxos a testar:**
- [ ] Acessa `/eventos/:id/editar` para um RASCUNHO próprio → formulário carregado com dados existentes
- [ ] Etapa 1 pré-preenchida com título e descrição do evento
- [ ] Alterar campos e salvar → PUT /api/events/:id → 200 → toast "Evento atualizado!" → redireciona para `/meus-eventos`
- [ ] Tentar editar evento PUBLICADO via URL direta → backend retorna 409 EVENTO_NAO_EDITAVEL → toast de erro
- [ ] Evento de outro promotor → backend retorna 404 → toast de erro → redireciona para `/meus-eventos`

---

## Navegação por papel

| Rota | PARTICIPANTE | PROMOTOR | ADMIN |
|---|---|---|---|
| `/eventos` | ✅ | ✅ | ✅ |
| `/eventos/:id` | ✅ | ✅ | ✅ |
| `/meus-eventos` | ❌ (redireciona `/`) | ✅ | ❌ (redireciona `/`) |
| `/eventos/novo` | ❌ | ✅ | ❌ |
| `/eventos/:id/editar` | ❌ | ✅ (owner) | ❌ |
| `/admin/usuarios` | ❌ | ❌ | ✅ |

**Nav items visíveis:**
- "Eventos" → todos os papéis
- "Meus eventos" e "Criar evento" (+ícone) → apenas PROMOTOR
- "Admin" → apenas ADMIN

---

## Estados a verificar em toda tela

| Estado | Comportamento esperado |
|---|---|
| Loading | Spinner centralizado; sem layout shift |
| Erro de rede | Mensagem clara + botão "Tentar novamente" |
| 401 | Interceptor do axios vai limpar token e redirecionar para login |
| 403 | Toast "Acesso restrito" |
| 404 | Mensagem "não encontrado" com link voltar |
| Vazio | Empty state com CTA para próxima ação |

---

## Critérios do PO a verificar no aceite

| Referência | Critério | Tela |
|---|---|---|
| US-020.1 | Wizard ≤ 3 etapas | CriarEditarEvento |
| US-020.4 | Campos faltando → erro inline sem perda de dados | CriarEditarEvento |
| US-020.5 | PAGO com preço=0 barrado | CriarEditarEvento |
| US-021.1 R1 | Botão Editar só para RASCUNHO | MeusEventos |
| US-021.2 | Publicar → status PUBLICADO + vagas = capacidade | MeusEventos |
| US-021.5 | Cancelar PUBLICADO → some da lista pública | Eventos |
| US-022.2 | Filtros combinados (q+tipo+data) | Eventos |
| US-022.4 | Estado vazio com mensagem | Eventos |
| US-023.3 | Datas no fuso do usuário (pt-BR) | EventoDetalhe |
| US-023.4 | Sem botão de inscrição | EventoDetalhe |
| R3 | vagasDisponiveis null → texto amigável | MeusEventos |
