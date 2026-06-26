# Sprint 1 — Validação PO da Arquitetura (fatia US-050 + US-051)

> Gerado pelo agente PO. Fonte: `architecture.md`, `api-contracts.md`, `data-model.md`, `tests-spec.md`, `po-planning.md`, `00-sprint-spec.md` (§5, §5.1, §13), `decisions.md` (ADR-P07).
> Escopo desta fatia: **US-051** (papel no JWT + header + guard ADMIN) + **US-050** (tela admin: listar/detalhar, aprovar/rejeitar promotores, máquina de estados ADR-P07). Fora: US-052, US-053, US-054.

---

## Histórias cobertas

- **US-051** ✅ — Plena cobertura por esta fatia.
- **US-050** ⚠️ — Parcialmente coberta: critérios de negócio plenos para aprovação/rejeição; dois critérios ficam PARCIAIS por dependência de US-052 e US-054 (detalhado abaixo).

---

## Cobertura critério a critério — US-051

| # | Critério operacional | Cobertura | Observação |
|---|---|---|---|
| 1 | Token com `papel: "ADMIN"` visível em jwt.io | **PLENA** | Novo overload `generateToken(id,email,verificado,papel)` em `JwtUtil`; `AuthService.login` passa o papel real. |
| 2 | Token de participante tem `papel: "PARTICIPANTE"` | **PLENA** | Mesmo mecanismo; caminho participante confirmado por teste `loginEmbutePapelNoToken`. |
| 3 | Token antigo (sem claim `papel`) não quebra — trata como PARTICIPANTE | **PLENA** | `validateToken` aplica default `"PARTICIPANTE"` quando ausente; retrocompat coberta por teste `tokenSemPapelValidaComoParticipante`; overload de 3 args preservado. |
| 4 | Bruno recebe 403 em `GET /api/users` via Postman | **PLENA** | `AdminController` lê `X-User-Papel` e lança `BusinessException(403, "Acesso restrito a administradores.")` para qualquer papel ≠ ADMIN. Testes D (matriz cross-papel) cobrem PARTICIPANTE/PROMOTOR/sem-header → 403. |
| 5 | Bruno não vê tela nem item "Admin" no front | **PLENA** | `AdminRoute` exige `papel === 'ADMIN'` → redireciona; nav condicional em `AppLayout` oculta o item. Testes F cobrem `redirecionaParticipante`, `redirecionaPromotor`, `itemAdminVisivelSomenteParaAdmin`. |
| 6 | Gateway encaminha `X-User-Papel` (verificável em log) | **PLENA** | `JwtAuthGlobalFilter` injeta `.header("X-User-Papel", user.papel())`. Whitelist exata implementada (ADR-T03). Teste B cobre injeção e whitelist. |

**US-051 ✅ — todos os 6 critérios plenamente cobertos por esta fatia.**

---

## Cobertura critério a critério — US-050

| # | Critério operacional (po-planning.md) | Status | Notas de produto |
|---|---|---|---|
| 1 | Admin loga com seed (`admin@pegaticket.local`) e vê item "Admin" no menu; participante não vê | **PLENA** | Seed idempotente na V3; guard de nav no front; teste F `itemAdminVisivelSomenteParaAdmin`. |
| 2 | Tela `/admin/usuarios` exibe tabela paginada com nome, e-mail, papel e status | **PLENA** | `GET /users` paginado + filtros `papel`/`status`/`q`; front tabela com colunas correspondentes; testes C listagem + F `renderizaTabelaComUsuarios`. |
| 3 | Admin filtra por "PROMOTOR / PENDENTE" e vê só cadastros pendentes | **PLENA** | Filtros `papel` e `status` no endpoint; teste C `filtraPorStatusPendente` + F `filtroDisparaNovaBusca`. |
| 4 | Admin vê **perfil completo** do promotor pendente no drawer | **PARCIAL → US-052** | O drawer desta fatia mostra só os campos existentes hoje: CPF, telefone, status, motivoRejeicao. Os campos ricos (e-mail de contato, endereço, redes sociais) são adicionados por US-052. A arquitetura preserva o seam: `PerfilResumoResponse` é extensível; US-052 estende o DTO e o drawer sem redesenho. |
| 5 | Admin aprova em ≤ 2 cliques → status muda para VERIFICADO sem reload | **PLENA** | `PUT /users/{id}/aprovar`; máquina de estados ADR-P07 (`aprovarComoPromotor()` + `perfil.aprovar()`); resposta 200 imediata com `UsuarioDetalheResponse` atualizado; toast `sonner` + atualização local. Teste C `aprovarPromovePapelEStatusEVerificado`. |
| 6 | Admin rejeita com motivo obrigatório → status REJEITADO e motivo visível no detalhe | **PLENA** | `PUT /users/{id}/rejeitar` com `RejeicaoRequest(@NotBlank @Size(max=300) motivo)`; `perfil.rejeitar(motivo)` grava `motivo_rejeicao`; coluna V3 na migration; drawer exibe `motivoRejeicao`. Testes C `rejeitarGravaStatusEMotivoEMantedPapelParticipante` + D `rejeitarSemMotivoRetorna400`. |
| 7 | Idempotência: duplo clique em Aprovar → 200, sem 500, estado segue VERIFICADO | **PLENA** | `aprovar` é no-op semântico se já PROMOTOR+VERIFICADO → retorna 200 com estado atual. Teste C `aprovarEhIdempotente`. |
| 8 | Participante via Postman em `PUT /api/users/{id}/aprovar` → 403 | **PLENA** | Defesa em profundidade: gateway (papel no header) + controller (lê header e decide). Teste D `participanteRecebe403` para todos os 4 endpoints. |
| E-mail de decisão (US-054) | Ao aprovar/rejeitar, Marina recebe e-mail | **PARCIAL → US-054** | O seam `afterCommit` está **reservado** (comentário/ponto de extensão em `aprovar` e `rejeitar`) mas não implementado. Marina não recebe e-mail nesta fatia — sabe da decisão só pelo painel. Comportamento aceitável como fatia intermediária; não bloqueia a demonstração da governança. |

---

## Aderência ao escopo

- [x] A fatia não introduz nenhuma feature fora do roadmap (Épico D / RF01). Os 4 endpoints admin + common-lib + gateway + tela frontend são exatamente o necessário para US-051 e US-050.
- [x] O deferimento de US-052 é aceitável: o drawer com CPF/telefone/status/motivo é suficiente para o Admin tomar a decisão de aprovar ou rejeitar em um contexto acadêmico/demo. A ausência de endereço e redes sociais não impede a demonstração do fluxo de governança.
- [x] O deferimento de US-053 (ativar/inativar) é correto e bem costurado: a coluna `ativo` não existe em V3 (será em V4); a coordenação de migrations (V3→V4) está documentada na `data-model.md` §6 e na `architecture.md` §8.
- [x] O deferimento de US-054 (e-mails de decisão) é aceitável com ressalva (ver abaixo). O seam `afterCommit` reservado é uma costura arquitetural adequada para não criar dívida silenciosa.
- [x] ADR-P07 é fielmente aplicado: promotor candidato nasce PARTICIPANTE + PerfilVerificado(PENDENTE); promoção a PROMOTOR só em `aprovar`; rejeição mantém PARTICIPANTE com motivo.
- [x] ADR-T03 (whitelist exata do gateway) é fechada corretamente dentro desta fatia — mudança pequena, inclusa por ser toque obrigatório no filtro.

---

## Pontos de atenção de produto

1. **Marina não sabe da decisão sem e-mail (US-054 deferido).** Nesta fatia, Marina descobre que foi aprovada ou rejeitada **apenas olhando o painel** (badge "Participante · solicitação em análise" ou papel "Promotor"). É aceitável como fatia intermediária: o painel reflete o estado real, e o seam de e-mail está reservado. Não é bloqueador, mas **deve ser primeira prioridade** na fatia US-054 — a experiência sem e-mail não serve ao usuário real fora do contexto de demo.

2. **Drawer com perfil incompleto pode comprometer a avaliação em cenários reais.** Com apenas CPF e telefone disponíveis (sem e-mail de contato, endereço, redes), o Admin tem informação mínima para avaliar o promotor. Em um contexto acadêmico/demo, é aceitável. O seam de extensão do `PerfilResumoResponse` está documentado e protegido — quando US-052 entrar, o drawer se completa sem redesenho. Registrar que a avaliação de promotor com drawer básico é uma **limitação temporária**, não um comportamento de produto definitivo.

3. **Placeholder do hash BCrypt na V3.** O SQL de seed contém `$2a$10$<HASH_GERADO_PELO_BACKEND>` — um hash inválido. Se o Backend esquecer de substituí-lo antes do commit, o admin não consegue logar (smoke test de login admin detecta isso imediatamente). Não é bloqueador arquitetural, mas o Backend deve gerar e colar o hash antes de abrir o PR. Registrado em ADR-T05.

4. **Sem ação "Inativar" na tela admin desta fatia (US-053 deferido).** O Admin verá a tela com apenas as ações Aprovar e Rejeitar por linha. A ausência de Ativar/Inativar é visível na UI de demo. Aceitável para esta fatia; US-053 completa o quadro de governança.

5. **`AdminRoute` e piscar de tela (race condition de loading).** O risco é documentado na `architecture.md` §8 e mitizado: `AdminRoute` aguarda `loading===false` antes de decidir; `signIn` seta `papel` otimista do `LoginResponse`. Verificar no teste F `mostraLoaderEnquantoCarrega` que o comportamento está coberto.

---

## Aprovação

**[x] APROVADO COM RESSALVAS**

As ressalvas são de produto (não técnicas), aceitas dentro do escopo da fatia:
- Critério US-050 §4 (perfil completo no drawer) fica PARCIAL até US-052.
- Critério US-050/US-054 (e-mail de decisão) fica PARCIAL até US-054.
- Drawer com perfil básico é demonstrável e coerente para o contexto acadêmico/demo.
- Nenhuma ressalva bloqueia a entrega ou a demonstração do fluxo central de governança (listar, filtrar, ver detalhe, aprovar, rejeitar, bloquear não-admin).

**Condição para aceite final (`po-acceptance.md`):** encenação dos cenários de Admin (aprovar + rejeitar + duplo clique + 403 para participante) e do cenário de Bruno (sem item Admin no menu, redireciona em `/admin/usuarios`) — todos verificáveis via Postman/UI sem dependência de US-052/053/054.
