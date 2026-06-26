# Sprint 2 — Handoff para o Frontend

> Autor: Backend Engineer. Data: 2026-06-26.
> Endpoints prontos e testados (54/54 verdes). Base URL via gateway: `/api/events/**`.

---

## Endpoints disponíveis

Todos leem `X-User-Id` (Long) e `X-User-Papel` (String: `PARTICIPANTE`|`PROMOTOR`|`ADMIN`) via header injetado pelo gateway. O frontend não precisa injetar — já vem do interceptor `api/client.ts` que envia `Authorization: Bearer <token>`.

### 1. `POST /api/events` — criar evento (PROMOTOR)

**Headers obrigatórios:** `X-User-Id`, `X-User-Papel: PROMOTOR`

**Request body:**
```json
{
  "titulo": "Show da Terra",
  "descricao": "Descrição longa opcional (até 5000 chars)",
  "dataInicio": "2026-08-01T14:00:00-03:00",
  "dataFim": "2026-08-01T18:00:00-03:00",
  "local": "Parque Central",
  "tipo": "GRATUITO",
  "capacidade": 200,
  "preco": null,
  "prazoReembolsoDias": null,
  "imagemUrl": null
}
```

Para evento PAGO: `"tipo": "PAGO"`, `"preco": 49.90` (> 0), `"prazoReembolsoDias": 7` (obrigatório).
GRATUITO: `preco` deve ser `null`.

**Response 201:**
```json
{
  "id": 1,
  "titulo": "Show da Terra",
  "descricao": "...",
  "dataInicio": "2026-08-01T17:00:00Z",
  "dataFim": "2026-08-01T21:00:00Z",
  "local": "Parque Central",
  "tipo": "GRATUITO",
  "status": "RASCUNHO",
  "capacidade": 200,
  "vagasDisponiveis": null,
  "preco": null,
  "prazoReembolsoDias": null,
  "imagemUrl": null,
  "promotorId": 42,
  "criadoEm": "2026-06-26T17:00:00Z",
  "atualizadoEm": "2026-06-26T17:00:00Z"
}
```

Header `Location: /events/{id}` na resposta.

**Erros:** `400` (campo ausente/inválido; PAGO sem preço; data_fim < data_inicio) · `401` (sem auth) · `403` (não PROMOTOR)

---

### 2. `GET /api/events/meus` — meus eventos (PROMOTOR)

**Headers:** `X-User-Id`, `X-User-Papel: PROMOTOR`
**Query params:** `page` (default 0), `size` (default 20), `sort` (default `criadoEm,desc`)

**Response 200:** `Page<EventoResumoResponse>` — todos os status (RASCUNHO/PUBLICADO/CANCELADO/REALIZADO).

```json
{
  "content": [
    {
      "id": 1,
      "titulo": "Show da Terra",
      "dataInicio": "2026-08-01T17:00:00Z",
      "dataFim": "2026-08-01T21:00:00Z",
      "local": "Parque Central",
      "tipo": "GRATUITO",
      "status": "RASCUNHO",
      "preco": null,
      "capacidade": 200,
      "imagemUrl": null
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

---

### 3. `PUT /api/events/{id}` — editar evento (PROMOTOR + owner, apenas RASCUNHO)

**Headers:** `X-User-Id`, `X-User-Papel: PROMOTOR`
**Body:** mesmo shape de `EventoCreateRequest`.

**Response 200:** `EventoResponse` completo.
**Erros:** `400` · `403` papel · `404` (inexistente ou não é dono) · `409 EVENTO_NAO_EDITAVEL` (já publicado/cancelado)

---

### 4. `POST /api/events/{id}/publicar` — publicar (PROMOTOR + owner)

**Headers:** `X-User-Id`, `X-User-Papel: PROMOTOR`
**Body:** vazio.

**Response 200:** `EventoResponse` com `status: PUBLICADO` e `vagasDisponiveis: <capacidade>`.
**Erros:** `403` · `404` · `409 EVENTO_JA_PUBLICADO` · `409 TRANSICAO_INVALIDA`

---

### 5. `POST /api/events/{id}/cancelar` — cancelar (PROMOTOR + owner)

**Headers:** `X-User-Id`, `X-User-Papel: PROMOTOR`
**Body:** vazio.

**Response 200:** `EventoResponse` com `status: CANCELADO`.
**Erros:** `403` · `404` · `409 EVENTO_JA_CANCELADO` · `409 TRANSICAO_INVALIDA`

---

### 6. `GET /api/events` — lista pública de eventos PUBLICADOS

**Headers:** `X-User-Id` (qualquer papel autenticado)
**Query params:**

| Param | Tipo | Descrição |
|---|---|---|
| `q` | string | busca case-insensitive em `titulo` E `local` |
| `tipo` | `GRATUITO`\|`PAGO` | filtra por tipo |
| `de` | ISO-8601 offset (ex: `2026-08-01T00:00:00-03:00`) | dataInicio >= de |
| `ate` | ISO-8601 offset | dataInicio <= ate |
| `page` | int (default 0) | |
| `size` | int (default 20, max 100) | |

**Response 200:** `Page<EventoResumoResponse>` — somente PUBLICADOS.

**Erros:** `400` (tipo inválido) · `401`

---

### 7. `GET /api/events/{id}` — detalhe

**Headers:** `X-User-Id` (qualquer papel)

**Comportamento:**
- PUBLICADO → visível a qualquer autenticado.
- RASCUNHO/CANCELADO/REALIZADO → só o owner vê; qualquer outro → **404**.

**Response 200:** `EventoResponse` completo (com `descricao`, `vagasDisponiveis`, `prazoReembolsoDias`).
**Erros:** `401` · `404`

---

## Shapes de DTO

### `EventoResponse` (detalhe completo — todos os endpoints de escrita + detalhe)

```typescript
interface EventoResponse {
  id: number;
  titulo: string;
  descricao: string | null;
  dataInicio: string;         // ISO-8601 UTC ex: "2026-08-01T17:00:00Z"
  dataFim: string;
  local: string;
  tipo: 'GRATUITO' | 'PAGO';
  status: 'RASCUNHO' | 'PUBLICADO' | 'REALIZADO' | 'CANCELADO';
  capacidade: number;
  vagasDisponiveis: number | null;  // null enquanto RASCUNHO
  preco: number | null;             // null se GRATUITO
  prazoReembolsoDias: number | null;
  imagemUrl: string | null;
  promotorId: number;
  criadoEm: string;
  atualizadoEm: string;
}
```

### `EventoResumoResponse` (item de lista — GET /events e GET /events/meus)

```typescript
interface EventoResumoResponse {
  id: number;
  titulo: string;
  dataInicio: string;
  dataFim: string;
  local: string;
  tipo: 'GRATUITO' | 'PAGO';
  status: 'RASCUNHO' | 'PUBLICADO' | 'REALIZADO' | 'CANCELADO';
  preco: number | null;
  capacidade: number;
  imagemUrl: string | null;
}
```

### `Page<T>` (paginação — formato Spring)

```typescript
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;   // página atual (0-indexed)
  size: number;
}
```

---

## Erros (padrão `ErrorResponse`)

```typescript
interface ErrorResponse {
  timestamp: string;   // ISO-8601
  status: number;
  error: string;       // ex: "Forbidden", "Not Found"
  message: string;     // ex: "EVENTO_JA_PUBLICADO", "Acesso restrito a promotores."
  path: string;
}
```

**Mapeamento de status → UX:**

| Status | Cenário | UX sugerida |
|---|---|---|
| 400 | Validação falhou | Erro inline por campo (campo `message` tem lista separada por `;`) |
| 401 | Não autenticado | Redirecionar para login |
| 403 | Papel insuficiente | Toast "Acesso restrito" |
| 404 | Não encontrado ou sem acesso | Página "Evento não encontrado" |
| 409 | Transição inválida / conflito | Toast com `message` (ex: "EVENTO_JA_PUBLICADO" → "Evento já está publicado") |
| 500 | Erro inesperado | Toast genérico "Algo deu errado, tente novamente" |

---

## Datas — fuso horário

- O backend persiste em **UTC** (`hibernate.jdbc.time_zone: UTC` + Postgres `TIMESTAMPTZ`).
- Datas na resposta chegam em **UTC ISO-8601** (ex: `"2026-08-01T17:00:00Z"`).
- **O front deve exibir no fuso do usuário** (`Intl.DateTimeFormat` ou `date-fns`/`dayjs`).
- Ao **enviar** datas no request, enviar ISO-8601 **com offset** (ex: `"2026-08-01T14:00:00-03:00"`). O back converte para UTC ao persistir.

---

## Notas de integração

- `POST /api/events/{id}/publicar` e `/cancelar` têm **body vazio** — não enviar `Content-Type: application/json` sem body (ou enviar com body `{}`).
- Filtro `q` em `GET /api/events` é case-insensitive e busca em `titulo` E `local` simultaneamente.
- `GET /api/events/meus` retorna **todos os status** do promotor (use badge de status para RASCUNHO/PUBLICADO/CANCELADO).
- Botão "Publicar" deve aparecer só para `status === 'RASCUNHO'`; "Cancelar" para `RASCUNHO` ou `PUBLICADO`.
- Sem botão de inscrição nesta sprint (Sprint 3).
