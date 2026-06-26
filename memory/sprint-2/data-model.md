# Sprint 2 — Modelo de Dados (event_db)

> Banco: `event_db` (Postgres 16). Schema versionado por **Flyway** (`ddl-auto: validate`). V1 já existe; esta sprint adiciona **V2**.
> Referência: `services/event-service/src/main/resources/db/migration/V1__init.sql`.

---

## Estado em V1 (já aplicado — não tocar)

`eventos`: `id`, `titulo VARCHAR(160)`, `descricao TEXT`, `data_inicio/data_fim TIMESTAMPTZ`, `local VARCHAR(200)`, `tipo VARCHAR(20) CHECK IN (GRATUITO,PAGO)`, `status VARCHAR(20) DEFAULT 'RASCUNHO' CHECK IN (RASCUNHO,PUBLICADO,REALIZADO,CANCELADO)`, `capacidade INTEGER CHECK > 0`, `preco NUMERIC(12,2)`, `prazo_reembolso_dias INTEGER`, `promotor_id BIGINT`, `criado_em/atualizado_em TIMESTAMPTZ DEFAULT NOW()`.
Constraints: `chk_datas (data_fim >= data_inicio)`, `chk_preco_pago (PAGO⇒preco>0 & prazo not null; GRATUITO⇒preco null)`.
Índices V1: `idx_eventos_promotor(promotor_id)`, `idx_eventos_status(status)`.
`avaliacoes`: **não usada nesta sprint** (Sprint 5).

---

## Migration nova — `V2__eventos_aux.sql`

Caminho: `services/event-service/src/main/resources/db/migration/V2__eventos_aux.sql`

```sql
-- Sprint 2: campos auxiliares de eventos.
-- vagas_disponiveis prepara o decremento atômico anti-overbooking (consumido na Sprint 3, ADR-T07).
-- Fica NULL enquanto RASCUNHO; o service inicializa = capacidade ao PUBLICAR.
ALTER TABLE eventos ADD COLUMN vagas_disponiveis INTEGER;
ALTER TABLE eventos ADD CONSTRAINT chk_vagas_nao_neg
    CHECK (vagas_disponiveis IS NULL OR vagas_disponiveis >= 0);

-- Imagem opcional (apenas URL — sem upload nesta sprint).
ALTER TABLE eventos ADD COLUMN imagem_url VARCHAR(300);

-- Índice parcial: a listagem publica filtra sempre status='PUBLICADO'.
-- Indice parcial mantem o B-tree pequeno (so as linhas publicadas) e cobre o caso quente.
CREATE INDEX idx_eventos_publicados ON eventos (status) WHERE status = 'PUBLICADO';
```

### Por que cada item

- **`vagas_disponiveis INTEGER` nullable + `CHECK >= 0`:** nullable porque só faz sentido após publicar (em RASCUNHO não há reserva). O `CHECK` é defesa em profundidade para o decremento atômico da Sprint 3 (`UPDATE ... WHERE vagas_disponiveis > 0` nunca deixa negativar; o CHECK garante mesmo se algo escapar). Permitir `NULL` evita ter que retro-preencher RASCUNHOs.
- **`imagem_url VARCHAR(300)`:** URL apenas; 300 cobre URLs longas com query string. Sem FK, sem validação de host (fora de escopo).
- **`idx_eventos_publicados` (parcial):** a query mais quente (`GET /events`) sempre tem `status='PUBLICADO'` no `WHERE`. O índice parcial é menor e mais seletivo que `idx_eventos_status` para esse caso. Mantemos `idx_eventos_status` (V1) para `GET /events/meus`/contagens por status.

### Reversibilidade (rollback mental)

V2 é **puramente aditiva** (sem `DROP`/sem alteração de tipo existente) → reversível sem perda:

```sql
-- rollback manual (não versionado; doc):
DROP INDEX IF EXISTS idx_eventos_publicados;
ALTER TABLE eventos DROP CONSTRAINT IF EXISTS chk_vagas_nao_neg;
ALTER TABLE eventos DROP COLUMN IF EXISTS imagem_url;
ALTER TABLE eventos DROP COLUMN IF EXISTS vagas_disponiveis;
```

Nenhuma coluna existente muda tipo/length (lição da Sprint 1: alterar `uf CHAR(2)`→`VARCHAR` quebrou `validate`). Nada destrutivo. Prod usa `baseline-on-migrate`.

> **Não** adicionar `IF NOT EXISTS` nos `ADD COLUMN`: Flyway versiona por checksum e roda cada migration uma vez; `ADD COLUMN IF NOT EXISTS` mascararia drift. Mantemos a forma simples (consistente com V1).

---

## Mapeamento entidade `Evento` ⇿ coluna (conferido contra V1 + V2)

> **Esta tabela é o contrato anti-`validate`-quebrado.** Cada `@Column` length/tipo casa exatamente com o DDL. Rodar `./mvnw -pl services/event-service test` cedo **e** smoke em Postgres real.

| Campo Java | Tipo Java | Coluna | Tipo SQL | Anotação JPA |
|---|---|---|---|---|
| `id` | `Long` | `id` | `BIGSERIAL` PK | `@Id @GeneratedValue(strategy = IDENTITY)` |
| `titulo` | `String` | `titulo` | `VARCHAR(160)` NN | `@Column(nullable=false, length=160)` |
| `descricao` | `String` | `descricao` | `TEXT` | `@Column(columnDefinition="TEXT")` |
| `dataInicio` | `OffsetDateTime` | `data_inicio` | `TIMESTAMPTZ` NN | `@Column(name="data_inicio", nullable=false)` |
| `dataFim` | `OffsetDateTime` | `data_fim` | `TIMESTAMPTZ` NN | `@Column(name="data_fim", nullable=false)` |
| `local` | `String` | `local` | `VARCHAR(200)` NN | `@Column(nullable=false, length=200)` |
| `tipo` | `TipoEvento` | `tipo` | `VARCHAR(20)` NN | `@Enumerated(STRING) @Column(nullable=false, length=20)` |
| `status` | `StatusEvento` | `status` | `VARCHAR(20)` NN | `@Enumerated(STRING) @Column(nullable=false, length=20)` |
| `capacidade` | `Integer` | `capacidade` | `INTEGER` NN | `@Column(nullable=false)` |
| `preco` | `BigDecimal` | `preco` | `NUMERIC(12,2)` | `@Column(precision=12, scale=2)` |
| `prazoReembolsoDias` | `Integer` | `prazo_reembolso_dias` | `INTEGER` | `@Column(name="prazo_reembolso_dias")` |
| `promotorId` | `Long` | `promotor_id` | `BIGINT` NN | `@Column(name="promotor_id", nullable=false)` |
| `criadoEm` | `OffsetDateTime` | `criado_em` | `TIMESTAMPTZ` NN | `@Column(name="criado_em", nullable=false)` |
| `atualizadoEm` | `OffsetDateTime` | `atualizado_em` | `TIMESTAMPTZ` NN | `@Column(name="atualizado_em", nullable=false)` |
| `vagasDisponiveis` | `Integer` | `vagas_disponiveis` | `INTEGER` (null) | `@Column(name="vagas_disponiveis")` |
| `imagemUrl` | `String` | `imagem_url` | `VARCHAR(300)` | `@Column(name="imagem_url", length=300)` |

### Armadilhas (validação Postgres)

1. **`local` é palavra-chave SQL** mas é um nome de coluna válido sem aspas no Postgres; o Hibernate gera identificador OK. Nenhuma ação, mas atenção em queries JPQL (`e.local` é seguro).
2. **`descricao TEXT`:** usar `columnDefinition="TEXT"`; **não** pôr `length` (geraria `varchar(255)` e o `validate` reclamaria contra `TEXT`).
3. **`preco`:** `@Column(precision=12, scale=2)` casa `NUMERIC(12,2)`. **Nunca** `Double`.
4. **`criadoEm`/`atualizadoEm` NOT NULL:** o `DEFAULT NOW()` do banco **não** salva o JPA de inserir `null` — a entidade seta no construtor; `atualizadoEm` no `@PreUpdate` (e também no construtor). Replicar o padrão de `Usuario`.
5. **`hibernate.jdbc.time_zone: UTC` ausente no `application.yml`** do event-service — **adicionar** (ver `architecture.md` §Timezone). Sem isso, `TIMESTAMPTZ` "anda" o horário.
6. **Enums:** os valores Java (`GRATUITO`/`PAGO`, `RASCUNHO`/...) batem com os `CHECK IN (...)` do V1 — qualquer divergência de nome quebra o insert (não o `validate`). Manter idênticos.

---

## Concorrência (preparação — não implementado nesta sprint)

`vagas_disponiveis` é **só preparado** aqui:
- Inicializado `= capacidade` ao **publicar** (no `EventService`).
- Sprint 3 (ADR-T07) implementa o decremento atômico:
  `UPDATE eventos SET vagas_disponiveis = vagas_disponiveis - 1 WHERE id = :id AND vagas_disponiveis > 0` (checar `rowsAffected==1`).
- O `chk_vagas_nao_neg` garante a invariante mesmo sob bug.

Nesta sprint **não** há mutação concorrente real → sem `@Version`, sem lock pessimista.
