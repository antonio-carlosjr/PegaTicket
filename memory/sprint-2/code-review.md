# Sprint 2 — Code Review (Eventos)

> Autor: Revisor (Principal Engineer, adversarial). Data: 2026-06-26. Branch: `feat/sprint-2-eventos`.
> Método: diff inteiro de `main...feat/sprint-2-eventos`. Foco: correção, concorrência, performance/N+1, segurança/authz, consistência com `api-contracts.md`.

## Resumo

- **Arquivos revisados:** backend (`Evento`, `EventService`, `EventRepository`, `EventController`, 4 DTOs, `GlobalExceptionHandler`, V2, 3 suites de teste, `application.yml`) + frontend (`api/events.ts`, `validation.ts`, 4 páginas, `PromotorRoute`, `AppRoutes`, `AppLayout`, 4 suites de teste).
- **Achados:** P0=0 · **P1=1** · **P2=2** · P3=3
- **Veredicto:** [x] CORRIGIR (P1/P2 aplicados pelo Revisor com teste de regressão) — após os fixes, **APROVADO**.
- **Testes:** backend `57/57` verdes (era 54; +3 regressão de type-mismatch). Frontend `51/52` verdes — a única falha (`Register.test.tsx`) é **BUG-001 pré-existente da Sprint 1** (label "E-mail" duplicado), não tocada por esta sprint. `npm run build` (tsc + vite) verde.

O backend está sólido: entity↔schema conferido col-a-col, máquina de estados na entidade, ownership→404 (não vaza existência), `CAST(:q AS string)` correto, `time_zone: UTC` presente, papel-antes-do-banco nas escritas, cobertura alta. Os defeitos reais estavam na **borda de parsing de query params** (back) e na **conversão de data do filtro** (front) — ambos invisíveis ao H2 e às suites existentes.

---

## P1 — Importantes

### CR-001 — Filtro de data da lista pública envia `datetime-local` sem offset → **500** (feature quebrada) ✅ CORRIGIDO
- **Local:** `frontend/src/pages/Eventos.tsx` — `buscar()` passava `de: de || undefined`, `ate: ate || undefined` crus.
- **Problema (defeito real):** os inputs "A partir de"/"Até" são `<input type="datetime-local">`, que produz `2026-06-20T14:00` **sem offset**. Esse valor era enviado cru à API. O backend faz bind de `de`/`ate` para `OffsetDateTime` via `@DateTimeFormat(iso=ISO.DATE_TIME)` — sem offset o bind falha. **Confirmado empiricamente:** `GET /events?de=2026-06-20T14:00` retorna **HTTP 500** (não 400). Ou seja: **qualquer participante que use o filtro de data (US-022.2 — filtros combinados) recebe erro 500.** A wizard de criação já convertia (`localParaIso`), mas a tela de listagem não — inconsistência que passou porque nenhum teste exercia o filtro de data ponta-a-ponta.
- **Correção aplicada:** adicionado helper `localParaIso()` em `Eventos.tsx` (espelha o da wizard) que converte o `datetime-local` para ISO-8601 com offset (`new Date(local).toISOString()`) antes de chamar a API; aplicado no único choke point (`buscar`), cobrindo valores vindos da URL.
- **Teste de regressão:** `Eventos.test.tsx` → "converte filtro de data (datetime-local) para ISO com offset antes de chamar a API" (renderiza com `?de=2026-06-20T14:00` e assere que `listarEventos` é chamado com string contendo offset `Z`/`±HH:MM`, e nunca o valor cru). **Verde.**

---

## P2 — Importantes (corrigidos por baixo custo/risco)

### CR-002 — `GlobalExceptionHandler` sem handler de `MethodArgumentTypeMismatchException` → param malformado vira **500** em vez de **400** (viola contrato) ✅ CORRIGIDO
- **Local:** `services/event-service/.../exception/GlobalExceptionHandler.java`.
- **Problema:** não há handler para `MethodArgumentTypeMismatchException`. Qualquer query param com tipo inválido cai no `@ExceptionHandler(Exception.class)` → **500 "Erro inesperado."**. `api-contracts.md` §6 diz explicitamente "`400` (`tipo` inválido / `de`/`ate` malformados)". **Confirmado empiricamente:** `?tipo=FOO` → **500**; `?de=2026-06-20T14:00` → **500**; `GET /events/abc` (id não-numérico) → **500**. Além de contrariar o contrato, mascara a causa real e gera ruído de 500 no monitoramento para erro do cliente.
- **Correção aplicada:** novo `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` → `ErrorResponse(400, "Bad Request", "Parametro '<nome>' com valor invalido.", path)`. Declarado **antes** do handler genérico (precedência de tipo mais específico no Spring garante o match correto de qualquer forma).
- **Teste de regressão:** `EventControllerIntegrationTest` → `listar_tipoInvalido_retorna400`, `listar_dataSemOffset_retorna400`, `detalhe_idNaoNumerico_retorna400`. **Verdes.**
- **Nota:** o CR-001 (front) e o CR-002 (back) são complementares — depois do fix do front, o caminho-feliz não dispara mais o 500; o fix do back garante 400 tipado caso qualquer cliente (ou chamada direta) mande param malformado. Defesa em profundidade.

### CR-003 — Validação de `@RequestBody` ocorre antes da checagem de papel (ordem de 403 vs 400) — RISCO BAIXO, **não corrigido** (documentado)
- **Local:** `EventController.criar/editar` — `@Valid @RequestBody` é resolvido pelo Spring **antes** do corpo do método (`requireUserId`/`requirePromotor`).
- **Problema:** um `PARTICIPANTE` enviando corpo malformado em `POST /events` recebe **400** (Bean Validation) em vez de **403**. O critério US-020.3 ("nada é criado quando 403") **continua satisfeito** — o `@Valid` não toca o banco. Mas há vazamento sutil de informação de validação a um não-promotor, e a ordem diverge da intenção "papel é a primeira coisa". O teste `participante_naoPoderCriarEvento_retorna403` passa apenas porque envia corpo **válido**.
- **Por que P2 e não corrigido:** corrigir exigiria mover a validação para dentro do método (perdendo o `@Valid` declarativo) ou um filtro/`@ControllerAdvice` de ordenação — complexidade desproporcional ao risco (a informação "escrever exige PROMOTOR" é pública, por design da arquitetura). **Devolvido ao owner (Backend)** como decisão consciente; se o PO considerar o vazamento de validação relevante, abrir história. Recomendação mínima: um teste documentando o comportamento atual (participante + corpo inválido → 400) para que não regrida silenciosamente.

---

## P3 — Melhorias (devolvidas ao owner, não corrigidas)

### CR-004 — `PromotorRoute` não distingue promotor **verificado** de **pendente**
- **Local:** `frontend/src/components/PromotorRoute.tsx` — checa só `user?.papel !== 'PROMOTOR'`.
- **Observação:** pela ADR-P07, todo promotor aprovado já é `papel=PROMOTOR` + `verificado=true`; um pendente permanece `PARTICIPANTE`. Logo, hoje, `papel==='PROMOTOR'` implica verificado e a rota está correta. Fica como **nota de invariante**: se algum dia existir `PROMOTOR` com `verificado=false`, esta rota o deixaria criar evento. Não é bug nesta sprint; só registrar a dependência da invariante ADR-P07.

### CR-005 — `EventoDetalhe`/`MeusEventos` confiam em `evento.imagemUrl` como `<img src>` sem allowlist
- **Local:** `EventoDetalhe.tsx:127`, `Eventos.tsx`/`MeusEventos.tsx` cards.
- **Observação:** `imagemUrl` é texto livre (validado só por `@Size(300)` no back e `max(300)` no Zod). Um promotor pode injetar uma URL arbitrária (ex.: tracking pixel, `javascript:` é inócuo em `src`, mas `http://` em página `https` gera mixed-content). Risco baixo (o autor é um promotor autenticado, e o alvo é o próprio público dele). Recomendação futura: validar esquema `https?://` no Zod e/ou `referrerPolicy="no-referrer"` na `<img>`. **Não bloqueia.**

### CR-006 — Cap de `size` no controller, mas `page`/`sort` sem validação; `sort` recebido e ignorado
- **Local:** `EventController.listar` — recebe `@RequestParam sort` (default `dataInicio,asc`) mas **nunca o usa** (constrói `PageRequest.of(page, cappedSize)` sem `Sort`). Parâmetro morto.
- **Observação:** a ordenação efetiva fica a cargo do default do banco (sem `ORDER BY` explícito na `@Query`, a ordem não é garantida — paginação pode repetir/pular itens entre páginas em teoria). Em `meus`, o `Pageable` do método é resolvido pelo Spring e respeita `sort`, mas em `listar` o `sort` é ignorado. Recomendação: ou remover o param `sort` (código morto, viola coding-standards §0.3) ou aplicá-lo via `Sort.by(...)` parseado, e garantir `ORDER BY` determinístico na listagem pública. **P3** — funcional hoje (o índice/scan tende a ordem estável em datasets pequenos), mas é dívida real. **Devolvido ao Backend.**

---

## Concorrência (avaliado conforme pedido — risco apontado, não bloqueia)

- **`publicar()`/`cancelar()`/`editar()` são read-modify-write sem `@Version`/lock.** Dois `publicar` simultâneos no mesmo evento: ambos leem RASCUNHO, ambos passam a invariante, ambos salvam PUBLICADO. Resultado final idêntico (idempotente-por-acaso) → **sem corrupção** nesta sprint. O risco real (decremento de `vagas_disponiveis`) é **Sprint 3** e está corretamente diferido (ADR-T07): o campo é só inicializado, nunca decrementado concorrentemente aqui. **Veredicto:** alinhado com `architecture.md` §Estratégias-1 e ADR-T07; **não adicionar `@Version` agora** (complexidade precoce). Quando a Sprint 3 introduzir reserva de vaga e edição de capacidade concorrente, o `UPDATE ... WHERE vagas_disponiveis > 0` + checagem de `rowsAffected` (ou `@Version`) torna-se obrigatório. Registrado para o Arquiteto da Sprint 3.

## N+1 / Performance

- **Sem N+1:** `Evento` é entidade chapada (sem `@OneToMany` mapeado; `avaliacoes` intencionalmente fora). Listagem e detalhe não disparam query por item. **OK.**
- **Query de listagem:** `CAST(:q AS string)` e `CAST(:de AS timestamp)` corretos — replicam o fix do `lower(bytea)` da Sprint 1. Índice parcial `idx_eventos_publicados` coerente com o `WHERE status='PUBLICADO'`. `idx_eventos_promotor` (V1) cobre `meus`. **OK** (ressalva de `ORDER BY` determinístico em CR-006).
- **Paginação:** sempre paginado, `size` capado em 100. **OK.**

## Segurança / Authz

- **403 (papel) vs 404 (ownership):** correto e testado (cross-papel e cross-owner). Ownership carrega o evento e lança `NotFoundException` com o **mesmo texto** do inexistente — não vaza existência. **OK.**
- **Papel-antes-do-banco:** `requirePromotor` é a primeira linha lógica nas escritas (ressalva CR-003 sobre `@Valid`). **OK.**
- **Gateway:** `/api/events/**` **não** está na whitelist do `JwtAuthGlobalFilter` → exige JWT válido. A dívida do `startsWith` na whitelist é **ADR-T03 (pré-existente da Sprint 1)**, não código novo desta sprint — não re-arquivada aqui.
- **Sem dado sensível em log:** `GlobalExceptionHandler` loga `ex.getMessage()` da integridade e stacktrace genérico; nenhum payload/credencial. **OK.**
- **Sem `catch(Exception){}` silencioso, sem `Optional.get()` cru, sem código morto** além do `sort` (CR-006). **OK.**

---

## Recorrências que viram regra (→ coding-standards / decisions)

1. **Toda borda que recebe data de `<input type="datetime-local">` DEVE converter para ISO-8601 com offset antes de enviar à API** (back faz bind para `OffsetDateTime`, que rejeita valor sem offset). Front: usar o helper `localParaIso`/`toISOString()`. → candidato a regra em `coding-standards.md` §2 (Frontend) e nota no contrato.
2. **`GlobalExceptionHandler` de todo serviço DEVE tratar `MethodArgumentTypeMismatchException` → 400** (param de query/path malformado nunca é 500). O user-service deveria ser auditado para o mesmo gap. → candidato a regra em `coding-standards.md` §1 (erros tipados) e a um handler compartilhado em `common-lib` quando houver a 3ª ocorrência (hoje 2: user + event).
3. **Suítes de integração devem exercitar filtros opcionais com valores realistas da UI** (incl. o formato exato que o input HTML emite), não só `null`/válidos — foi o ponto cego que escondeu CR-001/CR-002.
