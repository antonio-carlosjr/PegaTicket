# Sprint 1 — Identidade & Autorização · Spec Mestre (ultra-plan)

> Gerada por `/planejar-sprint 1` (orquestrador, ultra-plan). Fonte de verdade do sprint.
> Lê antes: [`architectural-plan.md`](../project/architectural-plan.md), [`backlog.md`](../project/backlog.md) (Épico D), [`decisions.md`](../project/decisions.md) (ADR-T01/T02/T03), [`rules/coding-standards.md`](../../rules/coding-standards.md).
> Gabarito de qualidade: o `user-service` atual (auth, reset, `PerfilVerificado`).

---

## 1. Objetivo (1 frase)

> **Ao fim deste sprint, o Admin consegue gerir usuários (aprovar/rejeitar/ativar/inativar promotores) por uma tela protegida, o Promotor se cadastra com perfil completo, e o sistema autoriza por papel de verdade** — fechando as dívidas ADR-T01/T02/T03 e destravando todo o lado-promotor das próximas sprints. (Roadmap §8: Épico D / RF01 endurecido.)

## 2. Escopo — histórias

| ID | História | Cabe em ~2 semanas porque… |
|---|---|---|
| **US-051** | Como sistema, quero que o **papel trafegue no token + header** (`X-User-Papel`) para autorização real | Toca `JwtUtil` (common-lib), gateway e login — pequeno em LOC, mas é **enabler** de tudo. Fecha **ADR-T01**. |
| **US-052** | Como promotor, quero me **cadastrar com perfil completo** (CPF, telefone, e-mail de contato, endereço, redes sociais) para ser avaliado pelo admin | Estende `perfis_verificados` + `RegisterRequest` + aba Promotor do front. Sem integrações externas (CEP manual). |
| **US-050** | Como admin, quero uma **tela de controle de usuários** para **aprovar/rejeitar** promotores pendentes e ver seus dados | Usa `aprovar()/rejeitar()` (hoje código morto) + endpoints protegidos. Fecha **ADR-T02**. |
| **US-053** | Como admin, quero **ativar/inativar** usuários para bloquear acesso de contas indevidas | Novo campo `usuarios.ativo` + checagem no login. Baixa complexidade, alto valor de governança. |

> Histórias **novas** (US-052, US-053) entram via expansão de escopo solicitada pelo dono → registrar **ADR-P06** em `decisions.md` (não é scope creep silencioso). ADR-T03 (whitelist exata) é fechada de carona (1 linha no gateway).

## 3. Serviços afetados

| Componente | Mudança |
|---|---|
| `shared/common-lib` | `JwtUtil` (+claim `papel`), `AuthenticatedUser` (+`papel`) — **retrocompatível** |
| `services/api-gateway` | injeta `X-User-Papel`; whitelist por match **exato** (ADR-T03) |
| `services/user-service` | migration V3, domínio (`Usuario.ativo`, `PerfilVerificado` campos+`aprovar/rejeitar`), `AdminController`, `AdminService`, login checa `ativo`, registro de promotor completo, seed admin |
| `frontend` | aba Promotor expandida; **nova tela `/admin/usuarios`** (só ADMIN); guard `AdminRoute`; nav condicional; `api/admin.ts` |

## 4. Delta de modelo de dados — `V3__perfil_promotor_e_controle_acesso.sql` (user_db)

```sql
-- Controle de acesso (US-053)
ALTER TABLE usuarios ADD COLUMN ativo BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX idx_usuarios_ativo ON usuarios(ativo);

-- Perfil completo do promotor (US-052) — colunas NULLABLE (linhas antigas + participante não têm)
ALTER TABLE perfis_verificados
  ADD COLUMN email_contato   VARCHAR(160),
  ADD COLUMN cep             VARCHAR(9),
  ADD COLUMN logradouro      VARCHAR(160),
  ADD COLUMN numero          VARCHAR(20),
  ADD COLUMN complemento     VARCHAR(80),
  ADD COLUMN bairro          VARCHAR(80),
  ADD COLUMN cidade          VARCHAR(80),
  ADD COLUMN uf              CHAR(2),
  ADD COLUMN instagram       VARCHAR(80),
  ADD COLUMN website         VARCHAR(200),
  ADD COLUMN motivo_rejeicao VARCHAR(300);
ALTER TABLE perfis_verificados
  ADD CONSTRAINT chk_perfis_uf CHECK (uf IS NULL OR uf ~ '^[A-Z]{2}$');

-- Seed ADMIN idempotente (ADMIN não pode nascer por cadastro público — ver AuthService)
-- senha dev: Admin@123  (hash BCrypt strength 10 gerado pelo Backend; dev-only)
INSERT INTO usuarios (nome, email, senha_hash, papel, verificado, ativo, criado_em)
VALUES ('Administrador', 'admin@pegaticket.local', '$2a$10$<HASH_GERADO_PELO_BACKEND>', 'ADMIN', TRUE, TRUE, NOW())
ON CONFLICT (email) DO NOTHING;
```

> Obrigatórios-na-borda para PROMOTOR (CPF, telefone, e-mail contato, CEP, logradouro, número, bairro, cidade, UF) são validados na **aplicação** (`RegisterRequest`), não no DB (colunas seguem nullable por causa de linhas legadas e do caminho participante). CPF continua `UNIQUE` no schema.

## 5. Autorização por papel (US-051 · fecha ADR-T01)

| Onde | O quê |
|---|---|
| `JwtUtil.generateToken(id, email, verificado, papel)` | novo claim `papel`; sobrecarga mantém compat. `validateToken` lê `papel`, **default `PARTICIPANTE`** se ausente (tokens antigos não quebram) |
| `AuthenticatedUser` | `record (Long id, String email, boolean verificado, String papel)` |
| `AuthService.login` | passa `u.getPapel().name()` ao gerar token |
| Gateway `JwtAuthGlobalFilter` | injeta `X-User-Papel`; whitelist **exata** (ADR-T03) |
| `user-service` admin endpoints | lê `@RequestHeader X-User-Papel`; se ≠ `ADMIN` → `BusinessException(403)` ("Acesso restrito a administradores."). Defesa em profundidade: gateway encaminha, serviço decide. |

## 5.1 Modelo de papel base & e-mails de status do promotor (NOVO — pedido do dono · ADR-P07)

**PARTICIPANTE é a role base de TODO usuário.** Consequências:
- Quem se candidata a promotor é criado/mantido como **PARTICIPANTE** + um `PerfilVerificado(status=PENDENTE)` com o perfil completo. **Já usa a plataforma como usuário comum** enquanto aguarda — não fica "preso".
- **Aprovação** (admin): papel **PARTICIPANTE → PROMOTOR** + `PerfilVerificado.status=VERIFICADO` + `verificado=true` → **dispara e-mail "aprovado"** (link para o painel).
- **Rejeição** (admin): papel **permanece PARTICIPANTE** + `status=REJEITADO` + `motivo_rejeicao` → **dispara e-mail "rejeitado" com o motivo**; o usuário **segue usando como participante** e pode **reenviar** a solicitação.
- **ADMIN** é seed (não nasce por cadastro público).
- Ajuste de código: `Usuario.novoPromotorPendente` passa a criar **PARTICIPANTE** (a promoção ocorre só em `aprovar()`). O badge do front "Promotor (pendente)" vira "Participante · solicitação em análise".

**Capacidades efetivas:** base/participante = qualquer autenticado **ativo**; promotor (criar evento — Sprint 2) = **papel PROMOTOR** (= aprovado); admin = **papel ADMIN**.

**E-mails (Thymeleaf + `EmailService`, disparo `afterCommit`):** `templates/email/promotor-aprovado.html` e `promotor-rejeitado.html` (vars `{nome, motivo, linkApp}`). Novo `PromotorStatusEmailService` espelhando o `WelcomeEmailService` (falha de envio só loga, não quebra a transação).

## 6. Endpoints novos / alterados (detalhe em `api-contracts.md` do Arquiteto)

| Método | Rota (via gateway `/api`) | Auth | O quê |
|---|---|---|---|
| POST | `/auth/register` | público | **estende** payload do promotor (perfil completo); CPF duplicado → 409 |
| POST | `/auth/login` | público | passa a **negar conta inativa** → 403 "Conta inativa" |
| GET | `/users` | ADMIN | lista/filtra usuários (`papel`, `status`, `ativo`, busca por nome/email) — paginado |
| GET | `/users/{id}` | ADMIN | detalhe (inclui `PerfilVerificado` completo do promotor) |
| PUT | `/users/{id}/aprovar` | ADMIN | promove papel→**PROMOTOR** + `aprovar()` + `verificado=true` (idempotente) → **e-mail "aprovado"** |
| PUT | `/users/{id}/rejeitar` | ADMIN | `rejeitar()` + `motivo_rejeicao` (body, **obrigatório**) → **e-mail "rejeitado" c/ motivo**; usuário continua PARTICIPANTE |
| POST | `/promotores/solicitar` | autenticado | usuário comum **solicita/reenvia** ser promotor: cria/atualiza `PerfilVerificado(PENDENTE)` com perfil completo (também usado na reaplicação pós-rejeição). CPF duplicado → 409 |
| PUT | `/users/{id}/ativar` | ADMIN | `usuarios.ativo=true` |
| PUT | `/users/{id}/inativar` | ADMIN | `usuarios.ativo=false` |
| ~~PUT~~ | ~~`/users/{id}/verify`~~ | — | **substituído** por `/aprovar` (fecha ADR-T02). Remover ou marcar deprecated. |

## 7. Frontend

- **Aba Promotor (Register) expandida** — seções: *Acesso* (nome, email, senha) · *Identificação* (CPF máscara) · *Contato* (telefone máscara, e-mail de contato) · *Endereço* (CEP máscara `00000-000`, logradouro, número, complemento, bairro, cidade, UF select) · *Redes* (Instagram, website — opcionais). Validação espelha o back (zod).
- **Nova tela `/admin/usuarios`** (rota protegida + `AdminRoute` papel===ADMIN): tabela com **filtros** (papel/status/ativo + busca), **ações** por linha (Aprovar/Rejeitar promotor, Ativar/Inativar), e **drawer de detalhe** mostrando o perfil completo do promotor. Empty/loading/error states (padrão do front).
- **Nav condicional:** item "Admin" só aparece para `papel === 'ADMIN'`.
- `api/admin.ts`: `listUsers`, `getUser`, `aprovar`, `rejeitar`, `ativar`, `inativar`.

## 8. Concorrência (pontos do sprint)

| Cenário | Estratégia |
|---|---|
| 2 cadastros de promotor com mesmo CPF quase simultâneos | `UNIQUE(cpf)` + capturar `DataIntegrityViolationException` → **409** "CPF já cadastrado" (mesma estratégia do e-mail) |
| Admin aprova 2x (duplo clique) | operação **idempotente**: aprovar já-aprovado = no-op 200 (ou 409 informativo) — definir no contrato |
| Inativar usuário logado | token (1h) segue válido até expirar — **limitação aceita** no MVP (sem blacklist); login futuro é bloqueado. Registrar em riscos. |

## 9. Dependências entre histórias

```
US-051 (papel no token)  ──►  US-050 (endpoints/tela admin)  ──►  US-053 (ativar/inativar usa a mesma tela/guard)
US-052 (perfil promotor) ──►  US-050 (detalhe mostra o perfil)  [pode correr em paralelo no back/front]
```
US-051 é **pré-requisito** (sem papel no header não há como autorizar ADMIN). Arquiteto entrega contratos de US-051 primeiro.

## 10. Riscos & mitigação

| Risco | Prob. | Impacto | Mitigação |
|---|---|---|---|
| Mudar `JwtUtil`/`AuthenticatedUser` quebra serviços/test existentes | Média | Alto | sobrecarga + default `PARTICIPANTE`; rodar `./mvnw verify` no reactor; ajustar testes do common-lib/gateway |
| Seed de admin com senha em claro no repo | Alta | Médio (dev) | dev-only, documentado; nota para tornar env-driven/remoção em prod (dívida ADR-T05) |
| Inativação não derruba sessão ativa (token stateless) | Alta | Baixo | aceito no MVP; registrar; refresh/blacklist é sprint futura |
| Tela admin exposta sem seed de admin → intestável | Média | Alto | seed na V3 garante 1 admin para demo/teste |
| Escopo maior que o usual (perfil + tela + authz) | Média | Médio | PO pode adiar "redes sociais" e filtros avançados para o fim; caminho-feliz primeiro |

## 11. Dívidas tocadas / fechadas

- **ADR-T01** (papel no token) → **fechada** por US-051.
- **ADR-T02** (`/verify` sem proteção) → **fechada** por US-050 (`/aprovar` protegido + status real).
- **ADR-T03** (whitelist por prefixo) → **fechada** (match exato no gateway).
- **ADR-T05 (nova)** — seed de admin dev-only: tornar credencial env-driven / processo de bootstrap seguro em prod. *Proposta.*

## 12. Fora de escopo (intencional)

- Integração ViaCEP/auto-preenchimento de endereço (CEP é manual).
- Edição de perfil pelo admin, troca de papel, exclusão de usuário (só aprovar/rejeitar + ativar/inativar).
- Tabela de auditoria dedicada (`audit_log`) — por ora, log estruturado nos endpoints admin.
- Refresh token / logout server-side / blacklist de token.
- Verificação por e-mail do `email_contato`; 2FA.

## 13. Critérios de sucesso verificáveis

1. Admin (seed) loga → vê **/admin/usuarios**; **não-admin** não vê o item de menu e recebe **403** ao chamar endpoints admin.
2. Promotor se cadastra com **perfil completo**; **CPF duplicado** é barrado com mensagem clara (409).
3. Admin **aprova** um promotor pendente → `PerfilVerificado.status = VERIFICADO` **e** `usuarios.verificado = true`.
4. Admin **inativa** um usuário → esse usuário **não consegue logar** (erro claro "conta inativa").
5. **Papel trafega no token**: token decodificado tem claim `papel`; gateway encaminha `X-User-Papel`.
6. `./mvnw -B verify` **verde** (incl. testes novos) e build do front sem erro de tipo.
7. **Aprovar** dispara e-mail "aprovado"; **rejeitar** dispara e-mail "rejeitado" **com o motivo** (visível no MailHog).
8. Promotor **pendente ou rejeitado** consegue **logar e usar a plataforma como participante** (role base).
9. Usuário **rejeitado reenvia** a solicitação → volta a `PENDENTE` (motivo limpo).

## 14. Testes-chave esperados (o Arquiteto detalha em `tests-spec.md`)

- **Autorização cross-papel:** participante autenticado → 403 em `GET /users`, `/aprovar`, `/inativar`.
- **CPF duplicado** sob cadastro concorrente → exatamente 1 sucesso, 1 × 409.
- **Login de inativo** → 403; reativar → login volta a funcionar.
- **Aprovação** muda `status` e `verificado` (e é idempotente).
- **Compat JWT:** token sem `papel` valida como `PARTICIPANTE` (não quebra serviços).
- **Frontend:** AdminRoute redireciona não-admin; form de promotor valida CPF/CEP mascarados; tabela de controle dispara as ações.

## 15. Decisões a registrar (ADR)

- **ADR-P06** — Expansão de escopo do Sprint 1 (US-052 perfil completo + US-053 ativar/inativar) aprovada pelo dono.
- **ADR-P07** — Modelo de **papel base**: PARTICIPANTE é a role base de todos; PROMOTOR é concedido só na aprovação; rejeição mantém PARTICIPANTE + permite reaplicar; aprovação/rejeição disparam e-mail (rejeição com motivo). Inclui US-054.
- **ADR-T05** — Seed de admin dev-only (dívida de bootstrap seguro).
- (T01/T02/T03 mudam status para **Aceita/Resolvida** ao fim do sprint.)

## 16. Definition of Done do Sprint

- [ ] US-050/051/052/053 com critérios do PO atendidos (aceite encarnando Admin/Marina).
- [ ] V3 aplicada e revertível; `ddl-auto: validate` passa.
- [ ] `papel` no token + `X-User-Papel` no gateway; endpoints admin 403 para não-admin.
- [ ] Cobertura nos services novos (`AdminService`, registro promotor) + testes cross-papel.
- [ ] Front: tela admin + form promotor, estados de UI, sem `any`/`console.log`.
- [ ] `./mvnw verify` e CI verdes; commits atômicos; PR aberto (via `/validar-sprint`).
- [ ] ADRs registradas; `backlog.md` atualizado; `retrospective.md` escrito.
