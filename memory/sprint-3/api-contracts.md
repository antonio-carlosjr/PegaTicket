# Sprint 3 — Contratos de API (Inscrição & Ingresso QR)

> Autor: Arquiteto. Front e Tester começam em paralelo a partir deste doc. Todos os DTOs são **`record` + Bean Validation**. Erros via `ErrorResponse` (`timestamp, status, error, message, path`) do `common-lib`. Mensagens de erro têm **código semântico** (constante em `message` ou prefixo) para o front mapear.
> Auth: o gateway injeta `X-User-Id`/`X-User-Email`/`X-User-Verified`/`X-User-Papel`. Endpoints públicos do ticket-service leem `X-User-Id`; ausência → **401**.

---

## Códigos de erro semânticos (referência do front)

| Código (em `message`) | HTTP | Significado |
|---|---|---|
| `JA_INSCRITO` | 409 | usuário já inscrito neste evento |
| `EVENTO_ESGOTADO` | 409 | sem vagas (`vagas_disponiveis = 0`) |
| `EVENTO_NAO_ENCONTRADO` | 404 | evento não existe |
| `EVENTO_NAO_PUBLICADO` | 422 | evento em RASCUNHO/CANCELADO/REALIZADO |
| `EVENTO_PAGO_NAO_SUPORTADO` | 422 | evento PAGO (Sprint 4) — bloqueado nesta sprint |
| `EVENTO_INDISPONIVEL` | 503 | event-service fora do ar / timeout |
| (Bean Validation) | 400 | corpo/param malformado |
| (X-User-Id ausente) | 401 | não autenticado |

> O front mapeia pela **string do código** dentro de `message`, não pelo texto humano (que pode mudar). Sugestão de contrato: `message` = `"EVENTO_ESGOTADO"` ou `"EVENTO_ESGOTADO: Evento esgotado."`. **Decisão:** `message` carrega o **código puro** (ex.: `"JA_INSCRITO"`) — o front traduz para texto amigável pt-BR. (Consistente com event-service Sprint 2, que usa `EVENTO_JA_PUBLICADO` etc. como `message`.)

---

## 1. POST /tickets/inscricoes  (via gateway: `/api/tickets/inscricoes`)

Inscreve o usuário autenticado num evento **GRATUITO** e **PUBLICADO**. Dispara a mini-saga (validar → reservar vaga → criar inscrição+ingresso). Idempotência via `UNIQUE(usuario_id, evento_id)`.

**Auth:** header `X-User-Id` (injetado pelo gateway). Ausente → 401.

### Request
```java
public record InscricaoRequest(
        @NotNull(message = "eventoId é obrigatório") Long eventoId
) {}
```

### Response 201 Created
`Location: /tickets/inscricoes/{id}`
```java
public record InscricaoResponse(
        Long id,                    // id da inscrição
        Long eventoId,
        String status,              // "ATIVA"
        OffsetDateTime inscritoEm,
        IngressoResponse ingresso   // o ingresso emitido (com codigo_unico p/ QR)
) {
    public static InscricaoResponse from(Inscricao i, Ingresso ing) { ... }
}

public record IngressoResponse(
        Long id,
        Long inscricaoId,
        String codigoUnico,         // UUID v4 — front renderiza o QR a partir disto
        String status,              // "ATIVO"
        OffsetDateTime emitidoEm
) {
    public static IngressoResponse from(Ingresso ing) { ... }
}
```

### Erros (`ErrorResponse` tipado)
| HTTP | `message` (código) | Quando |
|---|---|---|
| 400 | `eventoId é obrigatório` | body sem `eventoId` (Bean Validation) |
| 400 | `Parametro '...' com valor invalido.` | body com tipo errado (ex.: `eventoId` não-numérico) |
| 401 | `Autenticacao obrigatoria.` | `X-User-Id` ausente |
| 404 | `EVENTO_NAO_ENCONTRADO` | evento não existe |
| 409 | `JA_INSCRITO` | usuário já inscrito (constraint UNIQUE; inclui o caso concorrente) |
| 409 | `EVENTO_ESGOTADO` | sem vagas |
| 422 | `EVENTO_NAO_PUBLICADO` | evento não está PUBLICADO |
| 422 | `EVENTO_PAGO_NAO_SUPORTADO` | evento é PAGO (Sprint 4) |
| 503 | `EVENTO_INDISPONIVEL` | event-service fora/timeout (nenhuma vaga debitada) |

> **Idempotência / retry:** um re-POST de uma inscrição existente devolve **409 `JA_INSCRITO`** (não um segundo ingresso). O front, em erro de rede, deve consultar `GET /tickets/me` antes de re-tentar (ver handoff).

---

## 2. GET /tickets/me  (via gateway: `/api/tickets/me`)

Lista os **ingressos** do usuário autenticado (para a tela "Meus ingressos" com QR).

**Auth:** `X-User-Id`. Ausente → 401.

### Response 200 OK
```java
// Lista (não paginada nesta sprint — um usuário tem poucos ingressos;
// se crescer, paginar numa sprint futura). Sem N+1: join ingressos⨝inscricoes.
List<MeuIngressoResponse>

public record MeuIngressoResponse(
        Long ingressoId,
        String codigoUnico,         // p/ render do QR
        String statusIngresso,      // ATIVO | UTILIZADO | CANCELADO
        Long inscricaoId,
        Long eventoId,              // o front busca nome/data/local no event-service
        String statusInscricao,     // ATIVA | CANCELADA
        OffsetDateTime emitidoEm
) {}
```

> **Dados do evento (nome/data/local):** **não** vêm neste payload (evita fan-out REST N+1 cross-service). O front compõe com os dados do event-service (lista já carregada ou `GET /api/events/{id}`). Decisão registrada na arquitetura (§Performance).

### Erros
| HTTP | Quando |
|---|---|
| 401 | `X-User-Id` ausente |

Lista vazia → **200** com `[]` (o front mostra o estado vazio amigável — US-033.4).

---

## 3. GET /tickets/inscricoes/me  (via gateway: `/api/tickets/inscricoes/me`)

**Histórico** de inscrições do usuário, **paginado**, mais recente primeiro.

**Auth:** `X-User-Id`. Ausente → 401.

### Query params
| Param | Default | Obs |
|---|---|---|
| `page` | `0` | |
| `size` | `20` | cap em 100 (defesa, igual event-service) |
| `sort` | `inscritoEm,desc` | mais recente primeiro |

### Response 200 OK
`Page<InscricaoHistoricoResponse>` (envelope `Page` do Spring: `content`, `totalElements`, `totalPages`, `number`, `size`).
```java
public record InscricaoHistoricoResponse(
        Long id,
        Long eventoId,
        String status,              // ATIVA | CANCELADA
        OffsetDateTime inscritoEm
) {
    public static InscricaoHistoricoResponse from(Inscricao i) { ... }
}
```

### Erros
| HTTP | Quando |
|---|---|
| 400 | `page`/`size`/`sort` malformado (type mismatch → handler 400) |
| 401 | `X-User-Id` ausente |

---

## 4. POST /internal/events/{id}/reservar-vaga  (INTERNO — ticket→event)

**NÃO roteado pelo gateway.** Chamado service-to-service. Decremento atômico de `vagas_disponiveis`.

**Auth:** header **`X-Internal-Token`** == `${INTERNAL_SHARED_SECRET}`. Ausente/inválido → **403** (`ACESSO_INTERNO_NEGADO`). **Não** lê `X-User-*`.

### Request
Sem body. `{id}` é o evento.

### Response 200 OK
```java
public record ReservaResponse(Long eventoId, Integer vagasDisponiveis) {} // vagas após o decremento
```

### Erros
| HTTP | `message` | Quando |
|---|---|---|
| 403 | `ACESSO_INTERNO_NEGADO` | `X-Internal-Token` ausente/errado |
| 404 | `EVENTO_NAO_ENCONTRADO` | evento não existe |
| 409 | `EVENTO_ESGOTADO` | `vagas_disponiveis = 0` (rowsAffected=0 e evento publicado) |
| 422 | `EVENTO_NAO_PUBLICADO` | evento não está PUBLICADO (rowsAffected=0 por status) |

> **Como o service distingue 409 vs 422 quando `rowsAffected == 0`:** o `UPDATE` afeta 0 linhas tanto por esgotado quanto por não-publicado/inexistente. Só **nesse caminho frio** (rowsAffected=0) o service faz um `findById` para decidir: não existe → 404; status != PUBLICADO → 422; senão (publicado, vagas=0) → 409. O caminho quente (rowsAffected=1) **não** toca o banco de novo.

---

## 5. POST /internal/events/{id}/liberar-vaga  (INTERNO — compensação)

**NÃO roteado pelo gateway.** Incremento de `vagas_disponiveis` **limitado pela capacidade**, só se PUBLICADO. Idempotente no teto (no-op se já = capacidade).

**Auth:** `X-Internal-Token`. Ausente/inválido → 403.

### Request
Sem body. `{id}` é o evento.

### Response 200 OK
```java
public record ReservaResponse(Long eventoId, Integer vagasDisponiveis) {} // vagas após o incremento (ou inalterado se no teto)
```

### Erros
| HTTP | `message` | Quando |
|---|---|---|
| 403 | `ACESSO_INTERNO_NEGADO` | token ausente/errado |
| 404 | `EVENTO_NAO_ENCONTRADO` | evento não existe |

> Se `rowsAffected == 0` por já estar no teto (`vagas = capacidade`) ou por não-publicado → **200 no-op** (devolve o estado atual). Não é erro: a compensação ser idempotente/inofensiva é o comportamento desejado. Só 404 se o evento sumiu.

---

## Mapa gateway → serviço (confirmação de roteamento)

| Caminho público (cliente) | StripPrefix=1 → serviço | Roteado? |
|---|---|---|
| `POST /api/tickets/inscricoes` | ticket-service `POST /tickets/inscricoes` | ✅ rota `tickets` |
| `GET /api/tickets/me` | ticket-service `GET /tickets/me` | ✅ |
| `GET /api/tickets/inscricoes/me` | ticket-service `GET /tickets/inscricoes/me` | ✅ |
| `POST /api/internal/events/1/reservar-vaga` | — | ❌ **404 no gateway** (sem rota `/api/internal/**`) |
| `POST /api/events/1/reservar-vaga` | event-service `POST /events/1/reservar-vaga` | ⚠️ casaria a rota `events`, **mas esse endpoint não existe** (internos vivem em `/internal/...`) → 404 no event-service |

> A 2ª linha de baixo é o ponto crítico: como os internos **não** ficam sob `/events/...`, mesmo o wildcard `/api/events/**` não os alcança. Defesa de roteamento (ADR-T08). **Tester valida ambas as linhas `❌`/`⚠️`.**

---

## Notas para o Frontend (resumo — detalhe no handoff)

- **Inscrever:** só renderizar o botão "Inscrever-se" quando o evento for `tipo === 'GRATUITO'` **e** `status === 'PUBLICADO'`. Para PAGO, mostrar "Disponível em breve" (sem POST). Mapear 409 `JA_INSCRITO`/`EVENTO_ESGOTADO` e 422 para mensagens claras.
- **QR:** renderizar de `codigoUnico` (string) com lib JS de QR (nova dep — `qrcode.react` recomendada). Nunca pedir imagem ao backend.
- **Meus ingressos:** `GET /api/tickets/me` → lista; para nome/data/local do evento, compor com dados do event-service.
- **Histórico:** `GET /api/tickets/inscricoes/me` (paginado, `inscritoEm,desc`).
- **Datas:** as respostas trazem `OffsetDateTime` (com offset). Exibir no fuso do usuário. (Sem envio de datas pelo front nesta sprint — só `eventoId` no POST.)
- **Retry seguro:** em erro de rede no POST de inscrição, **não** re-POST cegamente; consultar `GET /api/tickets/me` para checar se a inscrição passou.
