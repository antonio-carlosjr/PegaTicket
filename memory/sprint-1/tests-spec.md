# Sprint 1 — Especificação de Testes / TDD (fatia US-050 + US-051)

> Autor: Arquiteto. O Tester escreve estes testes **vermelhos antes** do Backend/Frontend implementar.
> Backend: JUnit 5 + AssertJ + Spring Boot Test + **H2** (`application-test.yml`, RabbitMQ excluído) + MockMvc. `@MockBean` para `EmailService`/serviços de e-mail (não enviar de verdade).
> Frontend: Vitest + Testing Library (`renderWithProviders` de `src/test/utils.tsx`), `vi.mock` em `api/admin.ts` e `useAuth`.
> Convenção: nome de teste descreve o comportamento (não a implementação). Não testar "o service" genericamente.

---

## A. common-lib — `JwtUtilTest` (retrocompatibilidade US-051)

Arquivo: `shared/common-lib/src/test/java/com/ticketeira/common/security/JwtUtilTest.java` (estender o existente).

- `deveGerarEValidarTokenComClaims` — **mantém VERDE sem alteração** (não asserta papel; usa overload de 3 args). Garantia de retrocompat.
- `tokenSemPapelValidaComoParticipante` — gera token com `generateToken(id,email,verificado)` (3 args) → `validateToken(token).papel()` é `"PARTICIPANTE"`.
- `tokenComPapelAdminPreservaPapel` — `generateToken(1L,"a@a.com",true,"ADMIN")` → `validateToken(token).papel()` == `"ADMIN"`.
- `tokenComPapelPromotorPreservaPapel` — idem com `"PROMOTOR"`.
- `authenticatedUserExpoeQuatroCampos` — `validateToken` retorna record com id/email/verificado/papel coerentes.
- (Mantidos verdes: `deveRejeitarTokenAdulterado`, `deveRejeitarTokenComSecretDiferente`, `deveExigirSecretMinimo32Bytes`.)

> Antes de implementar, o Backend deve buscar usos de `new AuthenticatedUser(` no reactor (gateway/testes) e ajustar para o construtor de 4 args.

---

## B. api-gateway — `JwtAuthGlobalFilterTest` (US-051 + ADR-T03)

Arquivo novo: `services/api-gateway/src/test/java/com/ticketeira/gateway/filter/JwtAuthGlobalFilterTest.java`.
Estratégia: instanciar o filtro com um `JwtUtil` de teste; usar `MockServerWebExchange` + `GatewayFilterChain` mockado/captor para inspecionar o request mutado e a resposta.

- `injetaXUserPapelAPartirDoToken` — request com `Authorization: Bearer <token papel=ADMIN>` → o request encaminhado tem header `X-User-Papel: ADMIN` (além de X-User-Id/Email/Verified).
- `tokenSemPapelInjetaParticipante` — token de 3 args (sem papel) → `X-User-Papel: PARTICIPANTE`.
- `requisicaoSemBearerEm401` — rota protegida sem header `Authorization` → 401 (corpo JSON com message).
- `whitelistExataPermiteLoginSemToken` — `GET/POST /api/auth/login` passa sem token (chain chamada).
- `whitelistExataNaoCasaPrefixoEspurio` — `/api/auth/register-x` **NÃO** é whitelisted (exige token → 401). Fecha ADR-T03.
- `prefixoSwaggerContinuaLiberado` — `/swagger-ui/index.html` e `/v3/api-docs/swagger-config` passam sem token (prefixo preservado).
- `actuatorHealthExatoLiberado` — `/actuator/health` passa; `/actuator/healthz` (variação) exige token.

---

## C. user-service — `AdminServiceTest` (US-050, integração H2)

Arquivo novo: `services/user-service/src/test/java/com/ticketeira/user/service/AdminServiceTest.java`.
`@SpringBootTest @ActiveProfiles("test") @Transactional`, `@MockBean EmailService` (e qualquer serviço de e-mail de status, se já existir o seam). Fixtures via `AuthService.register` (promotor cria PARTICIPANTE+PerfilVerificado PENDENTE).

### Aprovar
- `aprovarPromovePapelEStatusEVerificado` — dado promotor pendente (PARTICIPANTE + perfil PENDENTE), `aprovar(id)` → usuário fica `papel=PROMOTOR`, `verificado=true`; perfil `status=VERIFICADO`; `motivoRejeicao=null`.
- `aprovarEhIdempotente` — chamar `aprovar(id)` **2×** seguidas → não lança; estado final `PROMOTOR`/`VERIFICADO`; nenhuma exceção/erro; segunda chamada retorna 200 (no-op de estado). (Cobre o duplo clique da §8 da spec.)
- `aprovarUsuarioSemPerfilLanca409` — usuário PARTICIPANTE puro (registrado sem perfil) → `aprovar(id)` lança `BusinessException` status 409, mensagem contém "solicitacao de promotor".
- `aprovarIdInexistenteLanca404` — `aprovar(999)` → `NotFoundException` (404).
- `aprovarReseta MotivoDeRejeicaoAnterior` — promotor antes REJEITADO (com motivo) → após `aprovar`, `motivoRejeicao` volta a `null` e `status=VERIFICADO`.

### Rejeitar
- `rejeitarGravaStatusEMotivoEManted PapelParticipante` — `rejeitar(id,"motivo X")` → perfil `status=REJEITADO`, `motivoRejeicao="motivo X"`; usuário **continua PARTICIPANTE**, `verificado` inalterado (false).
- `rejeitarUsuarioSemPerfilLanca409` — PARTICIPANTE puro → 409 ("solicitacao de promotor").
- `rejeitarIdInexistenteLanca404`.
- `rejeitarPermiteAtualizarMotivo` — rejeitar já-rejeitado com novo motivo → motivo atualizado, status segue REJEITADO (idempotência válida).

### Listagem
- `listaRetornaPaginaComStatusDoPerfil` — 1 admin + 1 promotor pendente + 1 participante → `listUsers(page0,size20)` retorna 3 itens; o promotor traz `status=PENDENTE`, participante e admin trazem `status=null`.
- `filtraPorPapelPromotor` — filtro `papel=PROMOTOR` retorna só promotores (após aprovação) / `papel=PARTICIPANTE` exclui admin.
- `filtraPorStatusPendente` — filtro `status=PENDENTE` retorna só quem tem perfil PENDENTE (exclui sem-perfil).
- `buscaPorNomeOuEmailCaseInsensitive` — `q="marina"` casa nome `Marina`; `q="ADMIN@"` casa email do admin.
- `paginacaoRespeitaPageSize` — com N>size, `totalElements`/`totalPages` corretos; `content.size()<=size`.
- `listaNaoFazNPlus1` — (teste de performance leve) inserir K promotores e assertar que a listagem dispara **uma** query principal — verificável via `@org.hibernate.stat`/Hibernate statistics ou contagem por `QueryCountHolder`/datasource-proxy se disponível; senão, documentar como inspeção manual e cobrir o shape (status presente sem segunda busca por linha).

### Detalhe
- `detalheInclui PerfilQuandoExiste` — promotor pendente → `getUser(id).perfil` não-nulo com cpf/telefone/status/motivoRejeicao.
- `detalheParticipantePerfilNull` — participante puro → `perfil == null`.
- `detalheIdInexistenteLanca404`.

---

## D. user-service — `AdminControllerTest` (US-050 + US-051 authz, MockMvc)

Arquivo novo: `services/user-service/src/test/java/com/ticketeira/user/controller/AdminControllerTest.java`.
`@WebMvcTest(AdminController.class)` com `AdminService` `@MockBean` **ou** `@SpringBootTest + MockMvc` (preferir slice se o guard de papel estiver no controller). Header `X-User-Papel` simulado por request.

### Autorização cross-papel (a matriz central da fatia)
Para cada endpoint admin (`GET /users`, `GET /users/{id}`, `PUT /users/{id}/aprovar`, `PUT /users/{id}/rejeitar`):
- `participanteRecebe403` — header `X-User-Papel: PARTICIPANTE` → **403** com message `Acesso restrito a administradores.`.
- `promotorRecebe403` — `X-User-Papel: PROMOTOR` → 403.
- `semHeaderPapelRecebe403` — sem `X-User-Papel` → **403** (decisão do Arquiteto: ausência = não-admin, não 401).
- `adminRecebe200` — `X-User-Papel: ADMIN` → 200 (service mockado retorna DTO).

### Contratos de erro/validação
- `rejeitarSemMotivoRetorna400` — ADMIN, body `{"motivo":""}` (ou ausente) → **400** (mensagem `motivo: ...`).
- `rejeitarComMotivoOk` — ADMIN, body `{"motivo":"texto"}` → 200; service recebe o motivo (captor).
- `aprovarUsuarioSemPerfilRetorna409` — service lança `BusinessException(409)` → controller responde 409 com a mensagem.
- `detalheInexistenteRetorna404`.
- `listaAceitaQueryParams` — `GET /users?page=1&size=5&papel=PROMOTOR&status=VERIFICADO&q=mar` → service chamado com os params corretos (captor); 200.

> O endpoint removido `/users/{id}/verify` **não** deve existir: opcional `verifyEndpointRemovido` — `PUT /users/{id}/verify` → 404/405 (rota inexistente).

---

## E. user-service — `AuthServiceTest` (delta US-051 + ADR-P07)

Estender/criar `services/user-service/src/test/java/com/ticketeira/user/service/AuthServiceTest.java`.

- `loginEmbutePapelNoToken` — registrar admin (ou usar seed), logar → decodificar o token retornado (via `JwtUtil.validateToken`) → `papel` == papel do usuário (ex.: `PARTICIPANTE` para participante).
- `registroDePromotorCriaParticipantePendente` — `register(papel=PROMOTOR, cpf, telefone)` → usuário salvo tem `papel=PARTICIPANTE` e `verificado=false` (ADR-P07, **corrige** o comportamento antigo de criar PROMOTOR); `PerfilVerificado` criado com `status=PENDENTE`.
- `registroParticipanteContinuaVerificado` — `register(papel=null)` → `PARTICIPANTE`, `verificado=true` (regressão: não quebrar o caminho participante).
- `registroAdminBloqueado403` — `register(papel=ADMIN)` → `BusinessException(403)` (regressão do comportamento existente).

> Observação de regressão: o `PasswordResetServiceTest` existente registra participante (`papel=null`) — **não deve quebrar** com a mudança de `novoPromotorPendente`. Rodar o reactor inteiro.

---

## F. Frontend — Vitest + Testing Library

### `AdminRoute` — `src/components/__tests__/AdminRoute.test.tsx`
- `redirecionaParticipante` — `useAuth` mockado com `user.papel='PARTICIPANTE'`, `loading=false`, token presente → renderiza `<Navigate to="/">` (assertar que o conteúdo protegido **não** aparece e há redirect).
- `redirecionaPromotor` — `papel='PROMOTOR'` → idem redirect.
- `permiteAdmin` — `papel='ADMIN'` → renderiza o filho protegido.
- `mostraLoaderEnquantoCarrega` — `loading=true` → não decide (renderiza loader, não redireciona).

### Nav condicional — `src/components/__tests__/AppLayout.test.tsx` (ou junto do existente)
- `itemAdminVisivelSomenteParaAdmin` — `papel='ADMIN'` → link "Admin" presente; `papel='PARTICIPANTE'`/`'PROMOTOR'` → ausente.

### Tela `/admin/usuarios` — `src/pages/admin/__tests__/Usuarios.test.tsx`
`vi.mock('@/api/admin')` retornando uma `PageResponse` fixa.
- `renderizaTabelaComUsuarios` — mock `listUsers` resolve 2 itens → tabela mostra nome/email/papel/status das linhas.
- `estadoLoading` — `listUsers` pendente → spinner/skeleton visível antes da resolução.
- `estadoEmpty` — `content: []` → mensagem de vazio (CTA), não tabela.
- `estadoErro` — `listUsers` rejeita → mensagem de erro clara (não "algo deu errado" cru); sem crash.
- `acaoAprovarChamaApi` — clicar "Aprovar" na linha → `aprovar(id)` chamado; toast de sucesso; (opcional) status atualiza para VERIFICADO sem reload.
- `rejeitarAbreModalEExigeMotivo` — clicar "Rejeitar" → abre modal; submeter com motivo vazio → erro inline, `rejeitar` **não** chamado; preencher motivo + confirmar → `rejeitar(id, motivo)` chamado.
- `filtroDisparaNovaBusca` — mudar select papel/status ou digitar em `q` → `listUsers` re-chamado com os params corretos.
- `drawerDetalheMostraPerfil` — abrir detalhe de um promotor → `getUser(id)` chamado; drawer mostra cpf/telefone/status/motivoRejeicao do perfil existente.

### `api/admin.ts` — `src/api/__tests__/admin.test.ts` (opcional, leve)
- `listUsersMontaQueryString` — `listUsers({page:1,papel:'PROMOTOR'})` → chama `api.get` com `/api/users` e params corretos (mock do axios `api`).

---

## G. Cobertura mínima (DoD desta fatia)

- `AdminService` (crítico — governança): **≥ 80%** (idealmente 90% nos caminhos aprovar/rejeitar/listar).
- `JwtUtil`/filtro do gateway: caminhos de papel cobertos (compat + 3 papéis).
- Authz cross-papel: matriz completa (PARTICIPANTE/PROMOTOR/sem-header → 403; ADMIN → 200) nos 4 endpoints.
- Concorrência: idempotência de aprovar (2×) é **obrigatória**.
- Frontend: happy path + ≥1 caminho de erro/validação por componente (AdminRoute, tela, modal de rejeição).

---

## H. Casos de borda explicitamente cobertos (resumo p/ rastreio)

| Caso | Esperado | Teste |
|---|---|---|
| Token antigo sem `papel` | trata como PARTICIPANTE | A `tokenSemPapelValidaComoParticipante` |
| Token ADMIN | papel preservado | A / B |
| `/api/auth/register-x` (prefixo espúrio) | exige token (401) | B `whitelistExataNaoCasaPrefixoEspurio` |
| Swagger subpath | liberado | B `prefixoSwaggerContinuaLiberado` |
| Não-admin em endpoint admin | 403 | D matriz |
| Sem header X-User-Papel | 403 | D `semHeaderPapelRecebe403` |
| Aprovar 2× (duplo clique) | 200 idempotente | C `aprovarEhIdempotente` |
| Aprovar/rejeitar sem perfil | 409 | C/D |
| Aprovar/rejeitar id inexistente | 404 | C/D |
| Rejeitar sem motivo | 400 | D `rejeitarSemMotivoRetorna400` |
| Promotor pós-registro | PARTICIPANTE + perfil PENDENTE | E `registroDePromotorCriaParticipantePendente` |
| Listagem com status do perfil | uma query, status presente | C `listaRetornaPaginaComStatusDoPerfil` / `listaNaoFazNPlus1` |
| AdminRoute não-admin | redirect | F `redirecionaParticipante` |
| Modal rejeitar exige motivo | bloqueia submit vazio | F `rejeitarAbreModalEExigeMotivo` |
