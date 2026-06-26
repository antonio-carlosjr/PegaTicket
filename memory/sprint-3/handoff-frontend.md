# Sprint 3 — Handoff Frontend (ticket-service endpoints)

> Para: Frontend Engineer | De: Backend Engineer | Data: 2026-06-26

Todos os endpoints do **ticket-service** estão em produção. Serviço sobe na porta `8083` (Docker Compose profile `backend`). O gateway roteia `/tickets/**` para `ticket-service:8083`.

---

## Autenticação

Todos os endpoints abaixo exigem o header `X-User-Id` (injetado pelo gateway a partir do JWT). O frontend **não** precisa enviar esse header diretamente — o gateway o injeta. Se o gateway não encontrar o JWT válido, devolve 401 antes de chegar ao ticket-service.

---

## Endpoints públicos (via gateway)

### 1. POST /tickets/inscricoes — Inscrever em evento

Inscreve o usuário autenticado em um evento **GRATUITO** e **PUBLICADO**. Dispara a mini-saga completa (valida → reserva vaga → cria inscricao + ingresso).

**Request**
```http
POST /tickets/inscricoes
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "eventoId": 42
}
```

**Response 201 Created**
```json
{
  "id": 7,
  "eventoId": 42,
  "status": "ATIVA",
  "inscritoEm": "2026-06-26T14:32:00Z",
  "ingresso": {
    "id": 7,
    "inscricaoId": 7,
    "codigoUnico": "a3f2e1d0-9b8c-4f7e-a1b2-c3d4e5f60718",
    "status": "ATIVO",
    "emitidoEm": "2026-06-26T14:32:00Z"
  }
}
```

**Erros possíveis**

| HTTP | `message` | Quando |
|---|---|---|
| 400 | `eventoId: must not be null` | Body sem `eventoId` |
| 400 | `Corpo da requisicao invalido.` | `eventoId` com tipo errado (ex.: string) |
| 401 | `Autenticacao obrigatoria.` | Gateway não injetou X-User-Id |
| 404 | `EVENTO_NAO_ENCONTRADO` | Evento não existe no event-service |
| 409 | `JA_INSCRITO` | Usuário já inscrito neste evento |
| 409 | `EVENTO_ESGOTADO` | Sem vagas disponíveis |
| 422 | `EVENTO_NAO_PUBLICADO` | Evento em rascunho/encerrado |
| 422 | `EVENTO_PAGO_NAO_SUPORTADO` | Evento do tipo PAGO (sprint 3 só suporta GRATUITO) |
| 503 | `EVENTO_INDISPONIVEL` | event-service fora do ar / timeout |

**Comportamento de retry (frontend):**
- Em **409 EVENTO_ESGOTADO**: exibir mensagem de esgotado, NÃO re-tentar.
- Em **503 EVENTO_INDISPONIVEL**: pode re-tentar após espera (exponential backoff).
- Em **409 JA_INSCRITO**: redirecionar para `/tickets/me` para mostrar o ingresso existente.

---

### 2. GET /tickets/me — Meus ingressos (tela QR)

Lista todos os ingressos ATIVOS do usuário autenticado. Use esta rota para a tela de "meus ingressos" com QR code.

**Nota importante**: a resposta inclui `eventoId` mas **não** inclui nome/título do evento. O frontend precisa compor com o event-service (`GET /events/{eventoId}`) para exibir o nome do evento. Isso é intencional: o ticket-service não replica dados do event-service (DB-per-service).

**Request**
```http
GET /tickets/me
Authorization: Bearer <jwt>
```

**Response 200 OK**
```json
[
  {
    "ingressoId": 7,
    "codigoUnico": "a3f2e1d0-9b8c-4f7e-a1b2-c3d4e5f60718",
    "statusIngresso": "ATIVO",
    "inscricaoId": 7,
    "eventoId": 42,
    "statusInscricao": "ATIVA",
    "emitidoEm": "2026-06-26T14:32:00Z"
  }
]
```

**QR Code**: gerar a partir de `codigoUnico` (string UUID v4, 36 chars). Sugestão: `qrcode.js` com valor `codigoUnico` diretamente (sem encoding adicional).

**Erros possíveis**

| HTTP | Quando |
|---|---|
| 401 | Não autenticado |

Lista vazia `[]` quando o usuário não tem ingressos — não é erro.

---

### 3. GET /tickets/inscricoes/me — Histórico de inscrições (paginado)

Lista o histórico de inscrições do usuário (mais recente primeiro). Útil para tela de "minhas inscrições" com status.

**Request**
```http
GET /tickets/inscricoes/me?page=0&size=20
Authorization: Bearer <jwt>
```

Parâmetros opcionais: `page` (0-based, default 0), `size` (default 20, máximo 100).

**Response 200 OK** (Spring Page)
```json
{
  "content": [
    {
      "id": 7,
      "eventoId": 42,
      "status": "ATIVA",
      "inscritoEm": "2026-06-26T14:32:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0,
  "first": true,
  "last": true
}
```

Valores possíveis de `status`: `ATIVA`, `CANCELADA`.

**Erros possíveis**

| HTTP | Quando |
|---|---|
| 401 | Não autenticado |

---

## Padrão de erro (todos os endpoints)

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "JA_INSCRITO",
  "path": "/tickets/inscricoes",
  "timestamp": "2026-06-26T14:32:00Z"
}
```

O campo `message` é o código de erro semântico para i18n/UX.

---

## Fluxo de tela recomendado — "Comprar ingresso"

```
[Tela Evento] → clica "Inscrever-se"
    ↓
POST /tickets/inscricoes  { eventoId }
    ↓ 201
[Tela Sucesso] → mostra codigoUnico como QR
    ↓ (ou link "Ver meus ingressos")
GET /tickets/me
    → para cada ingresso: GET /events/{eventoId}  (busca nome/data do evento)
[Tela Meus Ingressos] → lista com QR
```

**Em caso de 409 JA_INSCRITO**: ir direto para `GET /tickets/me` (não re-exibir formulário de inscrição).

---

## Variáveis de ambiente relevantes (ticket-service)

| Variável | Descrição | Default dev |
|---|---|---|
| `APP_EVENT_SERVICE_URL` | URL interna do event-service | `http://event-service:8082` |
| `INTERNAL_TOKEN` | Segredo compartilhado (ticket→event) | `dev-internal-secret` |
| `SPRING_DATASOURCE_URL` | JDBC do ticket_db | `jdbc:postgresql://postgres:5432/ticket_db` |

O frontend não precisa dessas variáveis — são internas ao backend.

---

## Endpoints internos (NÃO usar do frontend)

Os endpoints `/internal/events/{id}/reservar-vaga` e `/internal/events/{id}/liberar-vaga` são exclusivos para comunicação ticket-service → event-service. Exigem `X-Internal-Token` e **não são roteados pelo gateway**. O frontend nunca deve chamar esses endpoints diretamente.
