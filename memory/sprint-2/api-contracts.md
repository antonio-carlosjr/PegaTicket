# Sprint 2 — Contratos de API (Eventos)

> Base para Frontend e Tester começarem em paralelo. Rotas via gateway: `/api/events/**` (gateway faz `StripPrefix=1` → no event-service são `/events/**`).
> Auth: o serviço lê headers injetados pelo gateway — `X-User-Id` (Long), `X-User-Papel` (String: `PARTICIPANTE`/`PROMOTOR`/`ADMIN`). O serviço **não** valida JWT.
> Erros tipados via `ErrorResponse` de `common-lib`: `{ timestamp, status, error, message, path }`. O campo de **código semântico** abaixo (ex.: `EVENTO_JA_PUBLICADO`) é transmitido no `message` (padrão atual do projeto, que não tem campo `code` no `ErrorResponse`). Front mapeia por `status` + texto.

---

## Convenções de erro (todos os endpoints)

| Status | Quando | `message` (exemplos) |
|---|---|---|
| **400** | Bean Validation falhou (`@Valid`) | `titulo: não deve estar em branco; capacidade: deve ser >= 1` |
| **401** | `X-User-Id` ausente (não autenticado) | `Autenticação obrigatória.` |
| **403** | papel insuficiente (não-PROMOTOR em escrita) | `Acesso restrito a promotores.` |
| **404** | evento inexistente **ou** não visível ao chamador (RASCUNHO/CANCELADO alheio) | `Evento não encontrado.` |
| **409** | transição de estado inválida / conflito | `EVENTO_JA_PUBLICADO` / `TRANSICAO_INVALIDA` / `EVENTO_NAO_EDITAVEL` |
| **422** | regra de negócio na borda violada (ex.: PAGO sem preço — se não pega no 400) | `Evento PAGO exige preço maior que zero.` |
| **500** | inesperado | `Erro inesperado.` |

> **400 vs 422:** validações de **formato/presença de campo** (Bean Validation) → **400**. Regra de **coerência de negócio** que cruza campos (PAGO ⇒ preço>0 ⇒ prazoReembolso obrigatório) é validada via `@AssertTrue` no record (→ **400**) **e** defendida no service/CHECK do banco. Se a validação cross-field for feita no service (não anotada), lançar `BusinessException(422)`. **Preferência:** cross-field no record com `@AssertTrue` → 400 (mais simples, consistente com Bean Validation). Reservar 422 para regra que só o service conhece.

---

## DTOs compartilhados (records)

### Request — criar

```java
public record EventoCreateRequest(
        @NotBlank @Size(max = 160) String titulo,
        @Size(max = 5000) String descricao,                 // opcional (TEXT no banco; limite de sanidade)
        @NotNull @Future OffsetDateTime dataInicio,
        @NotNull OffsetDateTime dataFim,
        @NotBlank @Size(max = 200) String local,
        @NotNull TipoEvento tipo,                            // GRATUITO | PAGO
        @NotNull @Positive Integer capacidade,               // >= 1
        @PositiveOrZero BigDecimal preco,                    // obrigatório/>0 se PAGO (ver @AssertTrue)
        @PositiveOrZero Integer prazoReembolsoDias,          // obrigatório se PAGO
        @Size(max = 300) String imagemUrl                    // opcional, só URL
) {
    @AssertTrue(message = "data_fim deve ser maior ou igual a data_inicio")
    public boolean isPeriodoValido() {
        return dataInicio == null || dataFim == null || !dataFim.isBefore(dataInicio);
    }

    @AssertTrue(message = "Evento PAGO exige preço > 0 e prazo de reembolso; GRATUITO não deve ter preço")
    public boolean isPrecoCoerente() {
        if (tipo == TipoEvento.PAGO) {
            return preco != null && preco.signum() > 0 && prazoReembolsoDias != null;
        }
        // GRATUITO: sem preço (espelha o CHECK chk_preco_pago do V1)
        return preco == null;
    }
}
```

> As `@AssertTrue` espelham os `CHECK chk_datas` e `chk_preco_pago` do `V1__init.sql` — defesa em profundidade (borda + banco). Se o front mandar incoerente, é **400**; se passar pela borda (ex.: chamada direta), o `CHECK` do banco barra e o `GlobalExceptionHandler` traduz `DataIntegrityViolationException` → **409**.

### Request — editar

```java
public record EventoUpdateRequest(
        @NotBlank @Size(max = 160) String titulo,
        @Size(max = 5000) String descricao,
        @NotNull OffsetDateTime dataInicio,
        @NotNull OffsetDateTime dataFim,
        @NotBlank @Size(max = 200) String local,
        @NotNull TipoEvento tipo,
        @NotNull @Positive Integer capacidade,
        @PositiveOrZero BigDecimal preco,
        @PositiveOrZero Integer prazoReembolsoDias,
        @Size(max = 300) String imagemUrl
) {
    // mesmas @AssertTrue de período e preço coerente
}
```

> `dataInicio` no update **não** usa `@Future` (permite reeditar rascunho cuja data já era próxima sem falso-positivo de relógio; a coerência `dataFim>=dataInicio` continua). `status` e `promotorId` **não** entram no request (geridos pelo servidor).

### Response — detalhe completo

```java
public record EventoResponse(
        Long id,
        String titulo,
        String descricao,
        OffsetDateTime dataInicio,
        OffsetDateTime dataFim,
        String local,
        TipoEvento tipo,
        StatusEvento status,
        Integer capacidade,
        Integer vagasDisponiveis,     // null enquanto RASCUNHO; = capacidade após publicar
        BigDecimal preco,             // null se GRATUITO
        Integer prazoReembolsoDias,
        String imagemUrl,
        Long promotorId,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm
) {
    public static EventoResponse from(Evento e) { /* mapeamento direto */ }
}
```

### Response — item de lista (projeção enxuta)

```java
public record EventoResumoResponse(
        Long id,
        String titulo,
        OffsetDateTime dataInicio,
        OffsetDateTime dataFim,
        String local,
        TipoEvento tipo,
        StatusEvento status,
        BigDecimal preco,
        Integer capacidade,
        String imagemUrl
) {
    public static EventoResumoResponse from(Evento e) { /* ... */ }
}
```

---

## 1. `POST /events`  (via gateway: `POST /api/events`)
**Auth:** `X-User-Id` + `X-User-Papel == PROMOTOR`.
**Request:** `EventoCreateRequest` (acima).
**Comportamento:** cria com `status = RASCUNHO`, `promotorId = X-User-Id`, `vagasDisponiveis = null`. **Checa o papel ANTES de tocar o banco** (US-020.3).
**Response 201:** `EventoResponse` (com `id`, `status=RASCUNHO`). Header `Location: /events/{id}`.
**Erros:** `400` validação (título/data/capacidade ausentes, PAGO sem preço) · `401` sem `X-User-Id` · `403` não PROMOTOR.

## 2. `GET /events/meus`  (`/api/events/meus`)
**Auth:** `X-User-Id` + `X-User-Papel == PROMOTOR`.
**Query params:** `page` (default 0), `size` (default 20), `sort` (default `criadoEm,desc`).
**Comportamento:** retorna **todos os eventos do promotor logado** (`promotor_id = X-User-Id`), **qualquer status** (RASCUNHO/PUBLICADO/CANCELADO/REALIZADO).
**Response 200:** `Page<EventoResumoResponse>` (formato `Page` do Spring: `content[]`, `totalElements`, `totalPages`, `number`, `size`).
**Erros:** `401` · `403` não PROMOTOR.

## 3. `PUT /events/{id}`  (`/api/events/{id}`)
**Auth:** `X-User-Id` + `X-User-Papel == PROMOTOR` + **owner**.
**Request:** `EventoUpdateRequest`.
**Comportamento:** só edita se `status == RASCUNHO`. Atualiza campos, mantém status RASCUNHO, atualiza `atualizadoEm`.
**Response 200:** `EventoResponse` atualizado.
**Erros:**
- `400` validação · `401` sem header
- `403` não PROMOTOR (papel)
- `404` evento inexistente **ou não-owner** (não vaza existência — US-021.3)
- `409` `EVENTO_NAO_EDITAVEL` se `status != RASCUNHO`

## 4. `POST /events/{id}/publicar`  (`/api/events/{id}/publicar`)
**Auth:** `X-User-Id` + `PROMOTOR` + owner.
**Request:** vazio.
**Comportamento:** `RASCUNHO → PUBLICADO`; **inicializa `vagasDisponiveis = capacidade`** (preparação Sprint 3, ADR-T07); atualiza `atualizadoEm`. A partir daqui aparece em `GET /events`.
**Response 200:** `EventoResponse` (`status=PUBLICADO`, `vagasDisponiveis=capacidade`).
**Erros:**
- `401` · `403` papel · `404` inexistente/não-owner
- `409` `EVENTO_JA_PUBLICADO` (já PUBLICADO) · `409` `TRANSICAO_INVALIDA` (CANCELADO/REALIZADO → publicar)

## 5. `POST /events/{id}/cancelar`  (`/api/events/{id}/cancelar`)
**Auth:** `X-User-Id` + `PROMOTOR` + owner.
**Request:** vazio.
**Comportamento:** `RASCUNHO|PUBLICADO → CANCELADO`; some da lista pública. (Sprint 4+: dispara `evento.cancelado`/reembolso — **não** nesta sprint.)
**Response 200:** `EventoResponse` (`status=CANCELADO`).
**Erros:**
- `401` · `403` papel · `404` inexistente/não-owner
- `409` `EVENTO_JA_CANCELADO` (já CANCELADO) · `409` `TRANSICAO_INVALIDA` (REALIZADO → cancelar)

## 6. `GET /events`  (`/api/events`)  — lista pública
**Auth:** `X-User-Id` (qualquer papel autenticado ativo).
**Query params:**

| Param | Tipo | Default | Descrição |
|---|---|---|---|
| `q` | string | — | busca textual (case-insensitive) em `titulo` **e** `local` |
| `tipo` | `GRATUITO`\|`PAGO` | — | filtra por tipo |
| `de` | date-time (ISO offset) | — | `data_inicio >= de` |
| `ate` | date-time (ISO offset) | — | `data_inicio <= ate` |
| `page` | int | 0 | página |
| `size` | int | 20 | tamanho (cap em 100) |
| `sort` | string | `dataInicio,asc` | ordenação |

**Comportamento:** retorna **apenas `status = PUBLICADO`**. Filtros opcionais combináveis. **Sempre paginado** (nunca retorno irrestrito).
**Query (à prova de Postgres — replica a técnica do `UsuarioRepository`):**

```java
@Query("""
    SELECT e FROM Evento e
    WHERE e.status = com.ticketeira.event.domain.StatusEvento.PUBLICADO
      AND (CAST(:q AS string) IS NULL
           OR LOWER(e.titulo) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
           OR LOWER(e.local)  LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
      AND (:tipo IS NULL OR e.tipo = :tipo)
      AND (CAST(:de AS timestamp)  IS NULL OR e.dataInicio >= :de)
      AND (CAST(:ate AS timestamp) IS NULL OR e.dataInicio <= :ate)
    """)
Page<Evento> buscarPublicados(@Param("q") String q,
                              @Param("tipo") TipoEvento tipo,
                              @Param("de") OffsetDateTime de,
                              @Param("ate") OffsetDateTime ate,
                              Pageable pageable);
```

> **⚠️ `CAST(:q AS string)` é obrigatório** — sem ele, com `q=null`, o Postgres lança `function lower(bytea) does not exist` (bug exato da Sprint 1). O H2 do CI não reproduz; por isso a listagem com filtro null **deve ser testada em Postgres** (ver `tests-spec.md`). O `CAST(:de AS timestamp)` segue o mesmo princípio de defesa para os parâmetros de data opcionais.

**Response 200:** `Page<EventoResumoResponse>`.
**Erros:** `400` (`tipo` inválido / `de`/`ate` malformados) · `401`.

## 7. `GET /events/{id}`  (`/api/events/{id}`)  — detalhe
**Auth:** `X-User-Id` (qualquer papel).
**Comportamento:**
- `PUBLICADO` → visível a qualquer autenticado.
- `RASCUNHO`/`CANCELADO`/`REALIZADO` → visível **apenas ao owner** (`promotor_id == X-User-Id`). Para qualquer outro → **404** (não vaza existência — US-023.2).
**Response 200:** `EventoResponse` completo.
**Erros:** `401` · `404` inexistente **ou** não-publicado-alheio.

---

## Notas de implementação para o Backend

- **Ordem das rotas:** declarar `GET /events/meus` **antes** de `GET /events/{id}` no controller.
- **Papel-antes-do-banco:** nas escritas, `requirePromotor(papel)` é a **primeira** linha do método (US-020.3: nada é criado quando 403).
- **Ownership → 404:** o service carrega o evento; se `promotor_id != X-User-Id`, lança `NotFoundException("Evento não encontrado.")` (mesmo texto do inexistente).
- **Página `size` cap:** limitar `size` a 100 no controller (defesa contra `size` gigante).
- **`OffsetDateTime` na (de)serialização:** Jackson com `WRITE_DATES_AS_TIMESTAMPS=false` (ISO-8601). Já é o default do projeto (user-service serializa `criadoEm` como ISO).
