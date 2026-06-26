# Sprint 3 — Modelo de Dados (delta)

> Autor: Arquiteto. Conferido contra `services/ticket-service/.../V1__init.sql` e `event-service` V1+V2 (Sprint 2). Convenção: colunas `snake_case`; entidades/DTOs `camelCase`; `@Enumerated(STRING)` espelha o `CHECK`; PK `BIGSERIAL`; timestamps `TIMESTAMPTZ`. **Sem FK cross-service** (refs são `BIGINT`).

---

## Resumo executivo

- **`ticket_db`: SEM nova migration.** O `V1__init.sql` já tem `inscricoes`, `ingressos`, `checkins` com **todas** as constraints e índices que a Sprint 3 precisa. A sprint apenas **mapeia** `inscricoes` e `ingressos` em `@Entity` (1:1 com o DDL) e **usa** as constraints como mecanismo de concorrência. `checkins` **não** é mapeada (Sprint 5).
- **`event_db`: SEM nova migration.** `vagas_disponiveis` + `chk_vagas_nao_neg` (V2, Sprint 2) já existem. A Sprint 3 só **usa** a coluna via `UPDATE` atômico nos novos endpoints internos.
- **Sem AMQP nesta sprint** → **sem** tabela `processed_events` (entra na Sprint 4 com o caminho pago).

---

## 1. `ticket_db` — DDL existente (V1, referência — não recriar)

```sql
CREATE TABLE inscricoes (
    id           BIGSERIAL    PRIMARY KEY,
    usuario_id   BIGINT       NOT NULL,
    evento_id    BIGINT       NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ATIVA' CHECK (status IN ('ATIVA','CANCELADA')),
    inscrito_em  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_inscricao_usuario_evento UNIQUE (usuario_id, evento_id)
);

CREATE TABLE ingressos (
    id            BIGSERIAL    PRIMARY KEY,
    inscricao_id  BIGINT       NOT NULL UNIQUE REFERENCES inscricoes(id) ON DELETE CASCADE,
    codigo_unico  VARCHAR(64)  NOT NULL UNIQUE,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ATIVO' CHECK (status IN ('ATIVO','UTILIZADO','CANCELADO')),
    emitido_em    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE checkins (  -- Sprint 5; NÃO mapeada nesta sprint
    id             BIGSERIAL   PRIMARY KEY,
    ingresso_id    BIGINT      NOT NULL UNIQUE REFERENCES ingressos(id) ON DELETE CASCADE,
    realizado_em   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valido         BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_inscricoes_usuario  ON inscricoes(usuario_id);   -- cobre GET /tickets/me e /inscricoes/me
CREATE INDEX idx_inscricoes_evento   ON inscricoes(evento_id);
CREATE INDEX idx_ingressos_status    ON ingressos(status);
```

### Por que basta (cada requisito da sprint → mecanismo no V1)

| Requisito da sprint | Mecanismo no V1 | Comentário |
|---|---|---|
| Bloquear **dupla inscrição** (US-031) | `uk_inscricao_usuario_evento UNIQUE(usuario_id, evento_id)` | constraint é a defesa contra corrida; service captura `DataIntegrityViolationException` → 409 |
| **Um ingresso por inscrição** (US-032) | `ingressos.inscricao_id ... UNIQUE` | segunda emissão p/ a mesma inscrição falha |
| `codigo_unico` **global único** (US-032.5) | `ingressos.codigo_unico UNIQUE` | última linha de defesa contra colisão de UUID |
| Histórico paginado por usuário (US-033) | `idx_inscricoes_usuario` | filtro `usuario_id` indexado |
| Meus ingressos por usuário (US-033) | join `ingressos ⨝ inscricoes` em `inscricao_id` (PK) + `idx_inscricoes_usuario` | sem N+1 |

> **`ingressos.inscricao_id REFERENCES inscricoes(id)`** é uma FK **intra-serviço** (mesmo banco `ticket_db`) — permitida e desejável. **Não** é cross-service. `inscricoes.evento_id`/`usuario_id` são `BIGINT` puros (refs cross-service, sem FK — corretos).

---

## 2. Mapeamento entidade ⇿ coluna (conferido col-a-col contra V1)

> **Lição da Sprint 2 (`ddl-auto: validate` compara tipo E length):** cada linha conferida. Divergência de tipo/length quebra o boot no Postgres (o CI com H2 não pega).

### `Inscricao` → tabela `inscricoes`

| Coluna (SQL) | Tipo SQL | Campo Java | Tipo Java | Anotação JPA |
|---|---|---|---|---|
| `id` | `BIGSERIAL` PK | `id` | `Long` | `@Id @GeneratedValue(IDENTITY)` |
| `usuario_id` | `BIGINT` NN | `usuarioId` | `Long` | `@Column(name="usuario_id", nullable=false)` |
| `evento_id` | `BIGINT` NN | `eventoId` | `Long` | `@Column(name="evento_id", nullable=false)` |
| `status` | `VARCHAR(20)` NN | `status` | `StatusInscricao` | `@Enumerated(STRING) @Column(nullable=false, length=20)` |
| `inscrito_em` | `TIMESTAMPTZ` NN | `inscritoEm` | `OffsetDateTime` | `@Column(name="inscrito_em", nullable=false)` |

- Constraint `UNIQUE(usuario_id, evento_id)` **não** precisa ser declarada na entidade para funcionar (vive no banco); pode-se opcionalmente espelhá-la com `@Table(uniqueConstraints=...)` para documentação, mas com `ddl-auto: validate` o Hibernate não a cria — é informativa. **Decisão:** declarar `@Table(name="inscricoes")` simples; a constraint é do schema.
- `inscritoEm` **setado no factory** `Inscricao.criar(...)` (não confiar no `DEFAULT NOW()` do banco — lição Sprint 2).

### `Ingresso` → tabela `ingressos`

| Coluna (SQL) | Tipo SQL | Campo Java | Tipo Java | Anotação JPA |
|---|---|---|---|---|
| `id` | `BIGSERIAL` PK | `id` | `Long` | `@Id @GeneratedValue(IDENTITY)` |
| `inscricao_id` | `BIGINT` NN UNIQUE | `inscricaoId` | `Long` | `@Column(name="inscricao_id", nullable=false, unique=true)` |
| `codigo_unico` | `VARCHAR(64)` NN UNIQUE | `codigoUnico` | `String` | `@Column(name="codigo_unico", nullable=false, unique=true, length=64)` |
| `status` | `VARCHAR(20)` NN | `status` | `StatusIngresso` | `@Enumerated(STRING) @Column(nullable=false, length=20)` |
| `emitido_em` | `TIMESTAMPTZ` NN | `emitidoEm` | `OffsetDateTime` | `@Column(name="emitido_em", nullable=false)` |

- `inscricaoId` é **`Long`**, não `@OneToOne Inscricao` (decisão de modelagem na arquitetura: mesma tx, mesmo service, evita N+1). A FK + UNIQUE estão no banco.
- `codigoUnico` = `UUID.randomUUID().toString()` (36 chars, cabe em `VARCHAR(64)`). Setado no factory `Ingresso.emitir(inscricaoId)`.
- `emitidoEm` setado no factory.

### Enums (espelham os `CHECK`)

```java
public enum StatusInscricao { ATIVA, CANCELADA }            // só ATIVA é produzida nesta sprint
public enum StatusIngresso  { ATIVO, UTILIZADO, CANCELADO } // só ATIVO é produzido nesta sprint
```

> `UTILIZADO`/`CANCELADO`/`CANCELADA` existem no enum (espelham o `CHECK` do schema, e o Hibernate precisa do enum completo para ler valores que sprints futuras gravem), mas **nenhum código desta sprint os produz**. Não criar branch morto.

---

## 3. `event_db` — sem delta; uso da coluna existente

`vagas_disponiveis INTEGER` + `chk_vagas_nao_neg CHECK (vagas_disponiveis IS NULL OR vagas_disponiveis >= 0)` já existem (V2, Sprint 2). A entidade `Evento` já mapeia `vagasDisponiveis` (Sprint 2). A Sprint 3 adiciona **apenas queries** `@Modifying` no `EventRepository` (não alteram schema):

```java
@Modifying
@Query("""UPDATE Evento e SET e.vagasDisponiveis = e.vagasDisponiveis - 1
          WHERE e.id = :id
            AND e.status = com.ticketeira.event.domain.StatusEvento.PUBLICADO
            AND e.vagasDisponiveis > 0""")
int decrementarVaga(@Param("id") Long id);

@Modifying
@Query("""UPDATE Evento e SET e.vagasDisponiveis = e.vagasDisponiveis + 1
          WHERE e.id = :id
            AND e.status = com.ticketeira.event.domain.StatusEvento.PUBLICADO
            AND e.vagasDisponiveis < e.capacidade""")
int incrementarVaga(@Param("id") Long id);
```

- **Índice:** o `UPDATE ... WHERE id = :id` usa a PK (`id`) — já indexada. Nenhum índice novo.
- **`CHECK (vagas_disponiveis >= 0)`** é a rede de segurança contra negativo (defesa em profundidade).

---

## 4. Migrations — situação final

| Banco | Última migration existente | Nova nesta sprint? |
|---|---|---|
| `ticket_db` | `V1__init.sql` | **Não.** V1 basta. |
| `event_db` | `V2__eventos_aux.sql` (Sprint 2) | **Não.** Só queries novas (sem DDL). |

> **Confirmação para o DoD:** nenhum arquivo `V*.sql` novo é criado nesta sprint. Se durante a implementação surgir necessidade real de schema (não prevista), criar `V2__...sql` no ticket-service e atualizar este doc — mas a análise atual diz **sem delta**.

---

## 5. Timezone (gap a corrigir — não é migration)

O `ticket-service/src/main/resources/application.yml` **NÃO** tem `hibernate.jdbc.time_zone: UTC` (mesmo gap que o event-service tinha na Sprint 2). `inscrito_em`/`emitido_em` são `TIMESTAMPTZ`. **Ação obrigatória do Backend** (item do DoD), adicionar:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          time_zone: UTC
```

Sem isso, o Hibernate grava no fuso default da JVM e a leitura "anda" o horário. (Não afeta a lógica de inscrição, mas é correção de consistência transversal — UTC no banco, fuso na borda.)
