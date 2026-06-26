# Sprint 1 — Arquitetura Técnica (fatia US-050 + US-051)

> Autor: Arquiteto. Fonte de verdade técnica desta fatia. TDD-first: nenhum código de implementação aqui — só contratos, modelo e seam.
> Lê antes: [`00-sprint-spec.md`](00-sprint-spec.md) (§5, §5.1, §6, §8), [`po-planning.md`](po-planning.md) (US-050/051/054), [`architectural-plan.md`](../project/architectural-plan.md), [`decisions.md`](../project/decisions.md), [`coding-standards.md`](../../rules/coding-standards.md).
> Detalhes em: [`api-contracts.md`](api-contracts.md), [`data-model.md`](data-model.md), [`tests-spec.md`](tests-spec.md).

---

## 1. Histórias cobertas nesta fatia

- **US-051** (enabler, retrocompatível) — papel trafega no token JWT + header `X-User-Papel`; autorização por papel de verdade. **Fecha ADR-T01.**
- **US-050** (governança admin) — endpoints e tela admin protegidos para listar/detalhar usuários e **aprovar/rejeitar** promotores pendentes, com a máquina de estados de papel base (ADR-P07). **Fecha ADR-T02.**

> **FORA desta fatia** (deferido como seam, não desenhado em detalhe): US-052 (campos ricos de perfil), US-053 (`ativo` + bloqueio no login + ativar/inativar), US-054 (e-mails Thymeleaf + `POST /promotores/solicitar`). O ponto `afterCommit` para e-mail é apenas **reservado** (comentário/seam) em `/aprovar` e `/rejeitar`.

---

## 2. Serviço(s) afetado(s)

| Componente | Mudança nesta fatia |
|---|---|
| `shared/common-lib` | `JwtUtil` (+claim `papel`, overload de 4 args, default `PARTICIPANTE` em `validateToken`), `AuthenticatedUser` (+campo `String papel`) — **retrocompatível** |
| `services/api-gateway` | `JwtAuthGlobalFilter` injeta header `X-User-Papel`; whitelist passa a **match exato** com exceção de prefixo para Swagger/api-docs (ADR-T03) |
| `services/user-service` | migration **V3** (motivo_rejeicao + seed admin); `Usuario` (papel base + `aprovarComoPromotor()`); `PerfilVerificado` (+`motivoRejeicao`, `rejeitar(motivo)`); `AdminService` + `AdminController` (novos); `AuthService.login` passa papel ao token; `/users/{id}/verify` deprecado/removido |
| `frontend` | `api/admin.ts`; rota protegida `/admin/usuarios` + guard `AdminRoute`; nav condicional "Admin"; tabela paginada + filtros + drawer de detalhe + ações Aprovar/Rejeitar (modal motivo) |

---

## 3. Modelo de dados (delta) → detalhe em [`data-model.md`](data-model.md)

- **Sem entidades novas.** Alterações em entidades existentes do `user_db`:
  - `perfis_verificados` **+coluna** `motivo_rejeicao VARCHAR(300)` (nullable).
  - **Seed idempotente** do ADMIN (`admin@pegaticket.local`, papel `ADMIN`, `verificado=true`) — hash BCrypt gerado pelo Backend (placeholder no SQL). Dev-only (ADR-T05).
- **Migration:** `V3__motivo_rejeicao_e_seed_admin.sql` (próxima é V3; V1/V2 já existem).
- **NÃO inclui** coluna `ativo` (é US-053) nem campos ricos de perfil — `email_contato`, `cep`, endereço, redes (é US-052). Estas ficam para a migration V3 da fatia US-052/053, que o orquestrador deve ordenar (ver Riscos §8).
- **Índices novos para a listagem admin** (sem N+1): ver `data-model.md` §4.

---

## 4. Endpoints novos/alterados → detalhe em [`api-contracts.md`](api-contracts.md)

| Método | Rota (via gateway `/api`) | Auth | O quê |
|---|---|---|---|
| GET | `/users` | ADMIN | lista paginada + filtros `papel`, `status`, busca `q` (nome/email) |
| GET | `/users/{id}` | ADMIN | detalhe + `PerfilVerificado` existente (cpf, telefone, status, motivoRejeicao); 404 se inexistente |
| PUT | `/users/{id}/aprovar` | ADMIN | papel→PROMOTOR + `verificado=true` + status=VERIFICADO; **idempotente**; seam afterCommit p/ e-mail (US-054) |
| PUT | `/users/{id}/rejeitar` | ADMIN | body `{motivo}` obrigatório; status=REJEITADO + motivoRejeicao; papel permanece PARTICIPANTE; seam afterCommit |
| ~~PUT~~ | ~~`/users/{id}/verify`~~ | — | **removido** (substituído por `/aprovar`). Ver decisão §6 (ADR-T02). |
| POST | `/auth/login` | público | passa a embutir `papel` no token (US-051) — **sem** mudança de contrato de response (já devolve `papel`) |

> `/users/me` permanece inalterado (já devolve papel via `UsuarioResponse`).

---

## 5. Componentes

### 5.1 Backend (user-service)

- **Módulo `admin`** (novo): `AdminController` + `AdminService`.
  - `AdminController` (`@RequestMapping("/users")`): lê `@RequestHeader("X-User-Papel")` e exige `ADMIN` (defesa em profundidade — ver §7). Endpoints GET lista/detalhe, PUT aprovar/rejeitar. Controller fino; delega ao service.
  - `AdminService` (`@Transactional`): orquestra `UsuarioRepository` + `PerfilVerificadoRepository`. Reuso de `BusinessException`/`NotFoundException` (common-lib) e `GlobalExceptionHandler`.
- **Helper de autorização** (sem framework de roles, já que o serviço roda `permitAll`): um pequeno guard que lê `X-User-Papel` e lança `BusinessException("Acesso restrito a administradores.", 403)` se ≠ `ADMIN`. Aplicado nos 4 endpoints admin. Não criar abstração elaborada (3 ocorrências antes de extrair — aqui são 4 usos do mesmo método; um método estático/privado em `AdminController` basta).
- **Domínio:**
  - `Usuario`: `novoPromotorPendente` passa a criar **PARTICIPANTE** (não PROMOTOR) com `verificado=false`. Novo método `aprovarComoPromotor()` que faz `papel=PROMOTOR` + `verificado=true`. `marcarComoVerificado()` permanece (uso interno de `aprovarComoPromotor`). Comentário no Javadoc do enum `Papel.PROMOTOR` ajustado (hoje diz "começa verificado=false" — agora promotor só existe pós-aprovação).
  - `PerfilVerificado`: +campo `motivoRejeicao` (nullable). `rejeitar()` vira `rejeitar(String motivo)` que grava status=REJEITADO + motivo. `aprovar()` permanece (status=VERIFICADO) e **reseta** `motivoRejeicao=null` (reaprovar após rejeição limpa o motivo). Ambos deixam de ser código morto.
- **Reuso:** `AuthService` (sem mudança estrutural além de passar `u.getPapel().name()` ao `generateToken`), `UsuarioResponse.from`, `WelcomeEmailService` (padrão a ser espelhado por `PromotorStatusEmailService` na US-054 — não nesta fatia).
- **DTOs novos** (records + Bean Validation): `UsuarioListItem`, `UsuarioDetalheResponse`, `PerfilResumoResponse`, `RejeicaoRequest`, `PageResponse<T>`. Ver `api-contracts.md`.

### 5.2 common-lib

- `JwtUtil`: constante `CLAIM_PAPEL = "papel"`; default `PAPEL_DEFAULT = "PARTICIPANTE"`. `generateToken(id,email,verificado)` delega a `generateToken(id,email,verificado,"PARTICIPANTE")`. `validateToken` lê `papel` e usa default se ausente/nulo. **`JwtUtilTest` existente segue verde** (não asserta papel; o overload de 3 args continua existindo).
- `AuthenticatedUser`: `record (Long id, String email, boolean verificado, String papel)`.

### 5.3 api-gateway

- `JwtAuthGlobalFilter`: adiciona `.header("X-User-Papel", user.papel())` ao request mutado.
- Whitelist por **match exato** (ADR-T03), preservando comportamento de **prefixo apenas** para `/v3/api-docs` e `/swagger-ui` (subpaths do Swagger). Ver decisão §6.

### 5.4 Frontend

- `api/admin.ts`: `listUsers(params)`, `getUser(id)`, `aprovar(id)`, `rejeitar(id, motivo)` — tipados, via `api/client.ts` (sem `fetch` solto).
- `components/AdminRoute.tsx`: guard que, além de exigir token (como `ProtectedRoute`), checa `user.papel === 'ADMIN'`; senão `<Navigate to="/" replace />`. Espera `loading` resolver antes de decidir.
- `pages/admin/Usuarios.tsx`: tabela paginada (colunas nome/email/papel/status), barra de filtros (select papel + select status + input busca `q`), paginação (page/size), drawer/painel de detalhe com o perfil existente, ações por linha **Aprovar** e **Rejeitar** (Rejeitar abre modal com textarea de motivo obrigatório). Estados loading (skeleton/spinner), empty (CTA/mensagem), error (mensagem clara), success (toast `sonner`). Reuso de `components/ui/*` (`Button`, `Card`, `Badge`, `Input`, `Spinner`, `Tabs` se útil) — **drawer/modal:** Radix Dialog já está na stack (CVA + Radix); se não houver componente `dialog.tsx` ainda, criar um `ui/dialog.tsx` mínimo reusando Radix (1 componente novo de UI permitido).
- Nav condicional em `AppLayout.tsx`: item/link "Admin" → `/admin/usuarios` **apenas** quando `user.papel === 'ADMIN'`.
- Rota em `AppRoutes.tsx`: dentro do bloco `ProtectedRoute > AppLayout`, adicionar `/admin/usuarios` envolvido por `AdminRoute`.

---

## 6. ADRs desta fatia (deltas de status — orquestrador grava em `decisions.md`)

### ADR-T01 — Papel no JWT → **Resolvida (Aceita)** por US-051
O token passa a carregar o claim `papel`; o gateway injeta `X-User-Papel`. Tokens antigos (sem o claim) validam como `PARTICIPANTE` (default), sem quebrar serviços nem o `JwtUtilTest`. Autorização por papel agora é possível na borda (gateway) e em profundidade (serviço). **Texto proposto:** *"Aceita (Sprint 1). Claim `papel` adicionado ao JWT via overload retrocompatível em `JwtUtil`; `validateToken` aplica default `PARTICIPANTE` quando ausente. Gateway encaminha `X-User-Papel`. Fecha a dívida T01."*

### ADR-T02 — `/users/{id}/verify` sem proteção → **Resolvida (Aceita)** por US-050
Endpoint substituído por `PUT /users/{id}/aprovar`, protegido por papel ADMIN, que aplica a máquina de estados real (papel→PROMOTOR + `verificado=true` + `PerfilVerificado.status=VERIFICADO`). **Decisão tomada:** **remover** `/users/{id}/verify` (e `UserService.verificar`) nesta fatia — não há cliente legítimo (o front nunca o chamou; era código de dívida explicitamente marcado `// TODO`). Remover evita um endpoint não-autorizado vivo na superfície de ataque. **Texto proposto:** *"Aceita (Sprint 1). `/verify` removido; governança migra para `/aprovar` protegido por ADMIN com status real. Fecha T02."*

### ADR-T03 — Whitelist do gateway por prefixo → **Resolvida (Aceita)** com ressalva
**Decisão tomada: INCLUIR nesta fatia** (é mudança pequena e a fatia já toca o filtro). Trocar `startsWith` por match **exato** para as rotas de auth/actuator, **mantendo `startsWith` apenas** para `/v3/api-docs` e `/swagger-ui` (que legitimamente usam subpaths — ex.: `/swagger-ui/index.html`, `/v3/api-docs/swagger-config`). Implementação esperada: duas listas — `EXACT_WHITELIST` (login, register, forgot/reset, actuator/health, actuator/info) testada com `equals`, e `PREFIX_WHITELIST` (`/v3/api-docs`, `/swagger-ui`) testada com `startsWith`. Isso elimina o casamento espúrio `/api/auth/register-x` sem quebrar o Swagger. **Risco residual:** baixo; coberto por teste de filtro (ver `tests-spec.md`). **Texto proposto:** *"Aceita (Sprint 1). Whitelist exata para rotas fixas; prefixo preservado só para Swagger/api-docs. Fecha T03."*

### ADR-T05 — Seed de admin dev-only → **mantida como dívida (Proposta)**
A V3 semeia 1 admin com hash BCrypt em claro no repo (`Admin@123`, strength 10). Dev/demo apenas. **Texto proposto (reforço):** *"Proposta (dívida). Seed permanece dev-only; antes de prod, tornar credencial env-driven (ex.: bootstrap via variável de ambiente no boot do user-service) ou remover o seed. O placeholder `$2a$10$<HASH_GERADO_PELO_BACKEND>` no SQL deve ser substituído pelo Backend gerando o hash localmente — nunca commitar senha em claro."*

### ADR-P07 — Aplicação concreta do modelo de papel base
**Como esta fatia o encarna no código:**
- `Usuario.novoPromotorPendente(...)` cria **PARTICIPANTE** + `verificado=false`; o `PerfilVerificado` nasce `PENDENTE`. (Hoje cria PROMOTOR — esta é a correção do conflito apontado.)
- `aprovar` (admin): `usuario.aprovarComoPromotor()` (papel PARTICIPANTE→PROMOTOR + verificado=true) **e** `perfil.aprovar()` (status VERIFICADO, motivo limpo).
- `rejeitar` (admin): `perfil.rejeitar(motivo)` (status REJEITADO + motivo); **papel permanece PARTICIPANTE**, `verificado` inalterado.
- Capacidades efetivas: criar evento (Sprint 2) exige papel `PROMOTOR` (= aprovado), não apenas `verificado`. **Texto proposto:** *"Aplicado em Sprint 1: promotor candidato é PARTICIPANTE+PerfilVerificado(PENDENTE); promoção a PROMOTOR só em `aprovar`; rejeição mantém PARTICIPANTE."*

---

## 7. Estratégias críticas

- **Concorrência — duplo clique em Aprovar (US-050 §8):** operação **idempotente**. `aprovar` é um no-op semântico quando o usuário já é PROMOTOR + perfil VERIFICADO: retorna **200** com o estado atual (não 409). Implementação: o `AdminService.aprovar` carrega usuário+perfil dentro da `@Transactional`; se já está no estado-alvo, apenas retorna o DTO sem novas escritas (ou re-aplica os setters idempotentes — ambos convergem ao mesmo estado). Não há corrida destrutiva: dois aprovares concorrentes convergem ao mesmo estado final (idempotência por convergência de estado, sem necessidade de lock pessimista para este caso). Mesma lógica para rejeitar→rejeitar.
- **Alvo de `/aprovar` ou `/rejeitar` sem `PerfilVerificado` (decisão tomada):** retornar **409 Conflict** com mensagem `"Usuario nao possui solicitacao de promotor para avaliar."`. Justificativa: aprovar/rejeitar são ações **sobre uma solicitação de promotor**; um PARTICIPANTE puro (ou ADMIN) não tem solicitação — é um conflito de estado, não um "não encontrado" (o usuário existe) nem um erro de validação de input do request (422 seria sobre o corpo). 409 comunica "o recurso existe mas não está no estado que permite esta operação". O alvo inexistente (id desconhecido) → **404**.
- **Autorização (defesa em profundidade):** o gateway encaminha `X-User-Papel`; o user-service **decide** lendo o header. Sem o header (acesso direto sem gateway) → tratar como não-admin → **403** `"Acesso restrito a administradores."` (consistente com a mensagem do PO; não 401, pois a autenticação já foi feita no gateway — a ausência aqui significa "não posso confirmar ADMIN"). Decisão registrada para o Tester: **header ausente em endpoint admin = 403** (não 401).
- **Performance / sem N+1 (listagem):** a listagem junta `usuarios` + `perfis_verificados` (para a coluna `status`). Estratégia: query JPQL com **`LEFT JOIN`** explícito projetando direto para `UsuarioListItem` (constructor expression) — uma única query, sem lazy loading por linha. Filtro por `status` aplica no join; filtro por `papel` e busca `q` na tabela `usuarios`. Índices: `idx_usuarios_papel` (já existe, V2), `idx_perfis_status` (novo, V3) e `idx_perfis_usuario` (o `usuario_id` já é UNIQUE → indexado). Paginação via `Pageable` (page/size) → `count` + `select` paginado.
- **Imutabilidade do contrato JWT:** o overload de 3 args **não pode ser removido** (o `JwtUtilTest` e qualquer chamador legado dependem dele). Garantia de retrocompatibilidade verificada por teste (ver `tests-spec.md`).

---

## 8. Riscos técnicos

| Risco | Prob | Impacto | Mitigação |
|---|---|---|---|
| Mudar `Usuario.novoPromotorPendente` (PROMOTOR→PARTICIPANTE) quebra `PasswordResetServiceTest` ou outros que assumem o comportamento antigo | Baixa | Médio | Os testes existentes registram participante (`papel=null`); nenhum depende de promotor=PROMOTOR. Rodar `./mvnw verify` no reactor; ajustar fixtures se necessário |
| Alterar `AuthenticatedUser` (record +campo) quebra construções diretas em testes do gateway/common-lib | Média | Alto | `JwtUtilTest` constrói via `validateToken` (não `new`); `GatewayApplicationTests` é `@SpringBootTest` de contexto. Buscar `new AuthenticatedUser(` antes de implementar; ajustar |
| Conflito de numeração da migration V3 com a fatia US-052/053 (ambas querem ser V3) | **Alta** | Médio | **Decisão de coordenação:** esta fatia entrega `V3__motivo_rejeicao_e_seed_admin.sql`; a fatia US-052/053 usa `V4__perfil_promotor_e_controle_acesso.sql`. O orquestrador deve garantir a ordem. (A spec mestre §4 esboçava tudo numa V3; aqui split por fatia.) |
| Seed de admin com senha em claro vaza para prod | Alta | Médio (dev) | ADR-T05; documentado; placeholder de hash no SQL |
| Idempotência de aprovar mal-implementada gera 500 no duplo clique | Média | Médio | Teste explícito "aprovar 2× = 200 + estado VERIFICADO" (ver tests-spec) |
| Whitelist exata quebra Swagger por engano | Baixa | Médio | Prefixo preservado p/ `/v3/api-docs` e `/swagger-ui`; teste de filtro cobre ambos |
| Front: `useAuth` popula `user.papel` só após `refresh()` (chama `/users/me`); `AdminRoute` pode piscar | Média | Baixo | `AdminRoute` aguarda `loading===false` antes de decidir (mesmo padrão de `ProtectedRoute`); `signIn` já seta `papel` otimista do `LoginResponse` |

---

## 9. Definition of Done técnico (desta fatia)

- [ ] Migration `V3` aplicável e revertível mentalmente; `ddl-auto: validate` passa.
- [ ] `JwtUtil` retrocompatível (overload 3 args intacto; `JwtUtilTest` verde sem alteração de asserts).
- [ ] Gateway injeta `X-User-Papel`; whitelist exata + prefixo Swagger preservado.
- [ ] Endpoints admin retornam 403 para não-admin / header ausente; 200/404/409 conforme contrato.
- [ ] Aprovar idempotente; rejeitar grava motivo; papel base aplicado (ADR-P07).
- [ ] Listagem sem N+1 (uma query com join) + índices definidos.
- [ ] Cobertura ≥80% em `AdminService`; testes cross-papel e de concorrência presentes.
- [ ] Front: `AdminRoute` + tela + estados de UI; sem `any`/`console.log`; `npm run build` + `test:run` verdes.
- [ ] `./mvnw -B verify` verde no reactor inteiro; OpenAPI/contratos atualizados.
- [ ] Seam afterCommit reservado (comentado) em aprovar/rejeitar — **sem** implementar e-mail.
