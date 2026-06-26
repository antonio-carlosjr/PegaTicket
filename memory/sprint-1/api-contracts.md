# Sprint 1 — Contratos de API (fatia US-050 + US-051)

> Autor: Arquiteto. Front e Tester começam a partir daqui, em paralelo.
> Erros são `ErrorResponse` tipado (timestamp, status, error, message, path) traduzido por `GlobalExceptionHandler` a partir de `BusinessException`/`NotFoundException`.
> Todas as rotas admin passam pelo gateway sob `/api` e exigem **papel ADMIN** via header `X-User-Papel` (injetado pelo gateway a partir do claim do JWT). Defesa em profundidade: o serviço relê e decide.

---

## 0. US-051 — Papel no token e no header (enabler)

Não adiciona endpoints novos. Muda o **conteúdo** do JWT e os **headers** encaminhados.

### Token JWT (claims) — após esta fatia
```
iss: "ticketeira"
sub: "<userId>"
email: "<email>"
verificado: <boolean>
papel: "PARTICIPANTE" | "PROMOTOR" | "ADMIN"   ← NOVO claim
iat, exp (1h)
```
- Tokens **emitidos antes desta fatia** não têm `papel` → `JwtUtil.validateToken` aplica default `"PARTICIPANTE"`. Não quebram.

### Headers injetados pelo gateway (downstream)
```
X-User-Id:       <userId>
X-User-Email:    <email>
X-User-Verified: <boolean>
X-User-Papel:    PARTICIPANTE | PROMOTOR | ADMIN   ← NOVO header
```

### Contrato Java esperado (common-lib)
```java
// AuthenticatedUser — record com novo campo
public record AuthenticatedUser(Long id, String email, boolean verificado, String papel) {}

// JwtUtil — overload retrocompatível
public String generateToken(Long userId, String email, boolean verificado);                 // mantém: delega com papel "PARTICIPANTE"
public String generateToken(Long userId, String email, boolean verificado, String papel);   // novo
public AuthenticatedUser validateToken(String token);                                        // lê papel; default "PARTICIPANTE" se ausente/nulo
```

### `POST /auth/login` (público) — sem mudança de contrato externo
- Request/Response inalterados (`LoginResponse` já devolve `papel`).
- Mudança interna: `AuthService.login` chama `generateToken(id, email, verificado, u.getPapel().name())`.

---

## 1. GET /users   (via gateway: `/api/users`)

Lista paginada de usuários, com filtros. **Auth:** `X-User-Papel: ADMIN`.

### Query params
| Param | Tipo | Default | Observação |
|---|---|---|---|
| `page` | int | 0 | base 0 |
| `size` | int | 20 | máx 100 (acima → clamp a 100) |
| `papel` | enum | (nenhum) | `PARTICIPANTE` \| `PROMOTOR` \| `ADMIN`; filtra por papel do usuário |
| `status` | enum | (nenhum) | `PENDENTE` \| `VERIFICADO` \| `REJEITADO`; filtra pelo status do `PerfilVerificado` (usuários sem perfil são excluídos quando este filtro está presente) |
| `q` | string | (nenhum) | busca case-insensitive em nome **ou** email (`ILIKE %q%`) |

Ordenação default: `criadoEm DESC` (mais recentes primeiro). Sem param de sort nesta fatia.

### Response 200 — `PageResponse<UsuarioListItem>`
```java
record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}

record UsuarioListItem(
    Long id,
    String nome,
    String email,
    Papel papel,            // enum serializa como string
    boolean verificado,
    StatusVerificacao status, // status do PerfilVerificado; null se não houver perfil
    OffsetDateTime criadoEm
) {}
```
Exemplo:
```json
{
  "content": [
    { "id": 7, "nome": "Marina Souza", "email": "marina@ex.com",
      "papel": "PARTICIPANTE", "verificado": false, "status": "PENDENTE",
      "criadoEm": "2026-06-10T14:03:00Z" },
    { "id": 1, "nome": "Administrador", "email": "admin@pegaticket.local",
      "papel": "ADMIN", "verificado": true, "status": null,
      "criadoEm": "2026-06-07T00:00:00Z" }
  ],
  "page": 0, "size": 20, "totalElements": 2, "totalPages": 1
}
```

### Erros
- **403** `Acesso restrito a administradores.` — papel ≠ ADMIN **ou** header `X-User-Papel` ausente.

---

## 2. GET /users/{id}   (`/api/users/{id}`)

Detalhe de um usuário, incluindo o `PerfilVerificado` se existir. **Auth:** ADMIN.

### Response 200 — `UsuarioDetalheResponse`
```java
record UsuarioDetalheResponse(
    Long id,
    String nome,
    String email,
    Papel papel,
    boolean verificado,
    OffsetDateTime criadoEm,
    PerfilResumoResponse perfil   // null se o usuário não tem PerfilVerificado
) {}

// Apenas os campos que existem HOJE (US-052 estende depois — seam).
record PerfilResumoResponse(
    String cpf,
    String telefone,
    StatusVerificacao status,
    String motivoRejeicao,        // null exceto quando status == REJEITADO
    OffsetDateTime criadoEm
) {}
```
Exemplo (promotor pendente):
```json
{
  "id": 7, "nome": "Marina Souza", "email": "marina@ex.com",
  "papel": "PARTICIPANTE", "verificado": false,
  "criadoEm": "2026-06-10T14:03:00Z",
  "perfil": {
    "cpf": "123.456.789-00", "telefone": "(11) 98888-7777",
    "status": "PENDENTE", "motivoRejeicao": null,
    "criadoEm": "2026-06-10T14:03:00Z"
  }
}
```
Exemplo (participante puro): `"perfil": null`.

### Erros
- **403** não-admin / header ausente.
- **404** `Usuario nao encontrado.` — id inexistente.

---

## 3. PUT /users/{id}/aprovar   (`/api/users/{id}/aprovar`)

Aprova a solicitação de promotor. **Auth:** ADMIN. Sem body. **Idempotente.**

### Efeito (transacional)
1. `usuario.aprovarComoPromotor()` → papel `PARTICIPANTE → PROMOTOR`, `verificado = true`.
2. `perfil.aprovar()` → `status = VERIFICADO`, `motivoRejeicao = null`.
3. **Seam reservado (NÃO implementar nesta fatia):** registrar `afterCommit` para `PromotorStatusEmailService.enviarAprovado(usuario)` (US-054). Deixar apenas o comentário/ponto de extensão.

### Idempotência
- Se o usuário **já** é PROMOTOR + perfil VERIFICADO → **no-op**: retorna **200** com o estado atual (não 409, não 500).

### Response 200 — `UsuarioDetalheResponse` (mesmo shape do GET detalhe, refletindo o novo estado).

### Erros
- **403** não-admin / header ausente.
- **404** `Usuario nao encontrado.` — id inexistente.
- **409** `Usuario nao possui solicitacao de promotor para avaliar.` — usuário existe mas **não tem `PerfilVerificado`** (ex.: participante puro, admin). Decisão do Arquiteto (ver `architecture.md` §7): é conflito de estado, não 404 nem 422.

---

## 4. PUT /users/{id}/rejeitar   (`/api/users/{id}/rejeitar`)

Rejeita a solicitação de promotor com motivo obrigatório. **Auth:** ADMIN.

### Request — `RejeicaoRequest`
```java
record RejeicaoRequest(
    @NotBlank @Size(max = 300) String motivo
) {}
```
```json
{ "motivo": "CPF informado nao confere com o nome cadastrado." }
```

### Efeito (transacional)
1. `perfil.rejeitar(motivo)` → `status = REJEITADO`, `motivoRejeicao = motivo`.
2. `usuario` **inalterado**: papel permanece `PARTICIPANTE`, `verificado` inalterado.
3. **Seam reservado (NÃO implementar):** `afterCommit` → `PromotorStatusEmailService.enviarRejeitado(usuario, motivo)` (US-054).

### Idempotência
- Rejeitar um perfil já REJEITADO **atualiza o motivo** e retorna 200 (rejeitar de novo com outro motivo é operação válida do admin).

### Response 200 — `UsuarioDetalheResponse` refletindo `status=REJEITADO` + `motivoRejeicao`.

### Erros
- **400** `motivo: must not be blank` (formato `<campo>: <mensagem>` do `GlobalExceptionHandler`) — body com motivo vazio/ausente.
- **403** não-admin / header ausente.
- **404** `Usuario nao encontrado.` — id inexistente.
- **409** `Usuario nao possui solicitacao de promotor para avaliar.` — usuário sem `PerfilVerificado`.

---

## 5. Endpoint removido

### ~~PUT /users/{id}/verify~~ — **REMOVIDO**
Substituído por `/aprovar`. Remover o método em `UserController` e `UserService.verificar`. Fecha ADR-T02. (Sem cliente legítimo: o front nunca chamou; era dívida marcada `// TODO`.)

---

## 6. Catálogo de erros tipados (resumo)

| Status | Mensagem | Quando |
|---|---|---|
| 400 | `<campo>: <validação>` | Bean Validation falha (ex.: `motivo` em branco) |
| 403 | `Acesso restrito a administradores.` | `X-User-Papel` ≠ ADMIN ou ausente em endpoint admin |
| 404 | `Usuario nao encontrado.` | id inexistente em detalhe/aprovar/rejeitar |
| 409 | `Usuario nao possui solicitacao de promotor para avaliar.` | aprovar/rejeitar em usuário sem `PerfilVerificado` |

---

## 7. Frontend — contrato de `api/admin.ts`

```typescript
import type { Papel } from './auth'

export type StatusVerificacao = 'PENDENTE' | 'VERIFICADO' | 'REJEITADO'

export type UsuarioListItem = {
  id: number
  nome: string
  email: string
  papel: Papel
  verificado: boolean
  status: StatusVerificacao | null
  criadoEm: string
}

export type PerfilResumo = {
  cpf: string
  telefone: string
  status: StatusVerificacao
  motivoRejeicao: string | null
  criadoEm: string
}

export type UsuarioDetalhe = {
  id: number
  nome: string
  email: string
  papel: Papel
  verificado: boolean
  criadoEm: string
  perfil: PerfilResumo | null
}

export type PageResponse<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type ListUsersParams = {
  page?: number
  size?: number
  papel?: Papel
  status?: StatusVerificacao
  q?: string
}

export function listUsers(params: ListUsersParams): Promise<PageResponse<UsuarioListItem>>
export function getUser(id: number): Promise<UsuarioDetalhe>
export function aprovar(id: number): Promise<UsuarioDetalhe>
export function rejeitar(id: number, motivo: string): Promise<UsuarioDetalhe>
```
- Endpoints chamados: `GET /api/users`, `GET /api/users/{id}`, `PUT /api/users/{id}/aprovar`, `PUT /api/users/{id}/rejeitar`.
- Erros mapeados via `extractApiError` (já existe em `api/auth.ts`); 409 e 403 exibem a mensagem do back (toast/inline).
