# Sprint 4 — Modelo de Dados

> Migrations Flyway (ticket V2, payment V2) + entidades JPA novas (payment-service real, ProcessedEvent em ambos).
> `ddl-auto: validate` — schema versionado por Flyway, nunca gerado por Hibernate. `TIMESTAMPTZ` + `hibernate.jdbc.time_zone=UTC`.
> `event-service`: **sem migration** (so o DTO `EventoInternoResponse` muda — `preco`/`promotorId` ja existem em `eventos` V1).

---

## 1. ticket-service — `V2__saga_pagamento.sql`

```sql
-- Sprint 4: saga de inscricao paga (US-040/041) + idempotencia de consumidores (US-060).

-- 1) Ampliar o status da inscricao: novo estado intermediario PENDENTE_PAGAMENTO + EXPIRADA (TTL, ADR-T10).
--    Postgres nao tem "ALTER CHECK"; recria a constraint nomeada.
ALTER TABLE inscricoes DROP CONSTRAINT IF EXISTS inscricoes_status_check;
ALTER TABLE inscricoes
    ADD CONSTRAINT inscricoes_status_check
    CHECK (status IN ('ATIVA','CANCELADA','PENDENTE_PAGAMENTO','EXPIRADA'));

-- 2) Idempotencia de consumidor (pagamento.aprovado). event_id = UUID gerado na origem (payload).
CREATE TABLE processed_events (
    event_id      UUID         PRIMARY KEY,
    routing_key   VARCHAR(80)  NOT NULL,
    processado_em TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 3) Indice parcial p/ o job de TTL (ExpiracaoReservaJob varre so as pendentes).
CREATE INDEX idx_inscricoes_pendentes
    ON inscricoes (inscrito_em)
    WHERE status = 'PENDENTE_PAGAMENTO';
```
> `ingressos.inscricao_id` ja e `UNIQUE` (V1) — rede final para "1 ingresso por inscricao".
> Nota destrutiva: o `DROP CONSTRAINT IF EXISTS` recria o CHECK; e revertivel mentalmente (recriar com o conjunto antigo). Sem perda de dados.

---

## 2. payment-service — `V2__idempotencia.sql`

```sql
-- Sprint 4: idempotencia de consumidor (pedido.criado). US-060.
CREATE TABLE processed_events (
    event_id      UUID         PRIMARY KEY,
    routing_key   VARCHAR(80)  NOT NULL,
    processado_em TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```
> `pagamentos` / `reembolsos` / `configuracao_plataforma` ja existem em V1; `pagamentos.inscricao_id` ja e `UNIQUE` (rede final para "1 pagamento por inscricao"). `configuracao_plataforma` ja semeia `taxa_percentual=0.1000`. **Nenhuma alteracao nessas tabelas neste sprint.**

---

## 3. Enums e estados

### ticket-service — `StatusInscricao` (ampliado)
```java
public enum StatusInscricao {
    ATIVA,                // GRATUITO (S3) ou PAGO apos pagamento.aprovado
    CANCELADA,            // S5
    PENDENTE_PAGAMENTO,   // NOVO — PAGO, vaga reservada, sem ingresso
    EXPIRADA              // NOVO — TTL liberou a vaga (ADR-T10)
}
```
- `Inscricao.criar(usuarioId, eventoId)` (S3) -> `ATIVA` (mantido, GRATUITO).
- **Novo:** `Inscricao.pendentePagamento(usuarioId, eventoId)` -> `PENDENTE_PAGAMENTO`.
- **Novos metodos de transicao:** `ativar()` (PENDENTE_PAGAMENTO -> ATIVA, no consumidor de `pagamento.aprovado`); `expirar()` (PENDENTE_PAGAMENTO -> EXPIRADA, no job de TTL). Guardam o estado de origem (transicao invalida lanca).

### payment-service — `StatusPagamento`
```java
public enum StatusPagamento {
    PENDENTE,      // criado ao consumir pedido.criado (escrow ainda nao confirmado)
    CONFIRMADO,    // gateway aprovou; dinheiro RETIDO em escrow
    REEMBOLSADO,   // S5
    REPASSADO      // S5
}
```

---

## 4. Entidades JPA — payment-service (de stub a real; espelha o user-service)

### `Pagamento` (`@Entity @Table(name="pagamentos")`)
| Campo Java | Coluna | Tipo | Notas |
|---|---|---|---|
| `id` | `id` | `Long` (IDENTITY) | PK |
| `inscricaoId` | `inscricao_id` | `Long` | `UNIQUE` (V1) — 1 pagamento/inscricao |
| `usuarioId` | `usuario_id` | `Long` | ownership |
| `valorBruto` | `valor_bruto` | `BigDecimal` | NUMERIC(12,2) |
| `valorTaxa` | `valor_taxa` | `BigDecimal` | `round(bruto*0.10, 2)` (HALF_UP) |
| `valorRepasse` | `valor_repasse` | `BigDecimal` | `bruto - taxa` (computado, **nao liberado**) |
| `status` | `status` | `StatusPagamento` (`@Enumerated(STRING)`) | default `PENDENTE` |
| `gateway` | `gateway` | `String` | "SIMULADO" |
| `gatewayPaymentId` | `gateway_payment_id` | `String` | preenchido na confirmacao |
| `processadoEm` | `processado_em` | `OffsetDateTime` | set na confirmacao |
| `criadoEm` | `criado_em` | `OffsetDateTime` | default NOW() |

Factories / transicoes (regra no dominio, nao no service):
```java
// cria PENDENTE com escrow computado a partir do valor bruto e da taxa vigente
static Pagamento pendente(Long inscricaoId, Long usuarioId, BigDecimal valorBruto, BigDecimal taxaPercentual);

// PENDENTE -> CONFIRMADO (idempotente: no-op se ja CONFIRMADO; retorna boolean "transicionou")
boolean confirmar(String gatewayPaymentId);   // false => ja estava CONFIRMADO (nao republicar)
```

### `ConfiguracaoPlataforma` (`@Entity`, read-only no S4)
- `id`, `taxaPercentual` (`taxa_percentual NUMERIC(5,4)`), `vigenteDesde`. Service le a config vigente (a semente 0.1000) para calcular o escrow. Sem escrita neste sprint.

### `ProcessedEvent` (`@Entity @Table(name="processed_events")`)
```java
@Id @Column(name="event_id") private UUID eventId;   // do payload (origem)
@Column(name="routing_key", nullable=false, length=80) private String routingKey;
@Column(name="processado_em", nullable=false) private OffsetDateTime processadoEm;
static ProcessedEvent of(UUID eventId, String routingKey);
```

### `Reembolso` (`@Entity`, **read-only** no S4)
- Mapeada para satisfazer `ddl-auto: validate` (tabela `reembolsos` ja existe em V1) e para a listagem admin nao quebrar. **Nenhuma escrita** neste sprint (US-042 e S5).

### Repositorios (payment-service)
```java
interface PagamentoRepository extends JpaRepository<Pagamento, Long> {
    Optional<Pagamento> findByInscricaoId(Long inscricaoId);

    // lock pessimista p/ serializar dupla confirmacao concorrente do mesmo inscricaoId
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pagamento p WHERE p.inscricaoId = :id")
    Optional<Pagamento> findByInscricaoIdForUpdate(@Param("id") Long id);

    List<Pagamento> findByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);
    Page<Pagamento> findAll(Pageable pageable);                 // admin
    Page<Pagamento> findByStatus(StatusPagamento status, Pageable pageable);  // admin filtrado
}
interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {}
interface ConfiguracaoPlataformaRepository extends JpaRepository<ConfiguracaoPlataforma, Long> {
    Optional<ConfiguracaoPlataforma> findFirstByOrderByVigenteDesdeDesc();
}
```

---

## 5. Entidades JPA — ticket-service (delta)

### `ProcessedEvent` (`@Entity @Table(name="processed_events")`) — identico ao do payment
```java
@Id @Column(name="event_id") private UUID eventId;
@Column(name="routing_key", nullable=false, length=80) private String routingKey;
@Column(name="processado_em", nullable=false) private OffsetDateTime processadoEm;
```
Repositorio: `interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {}`.

### `Inscricao` (delta)
- Novos factory/transicoes (vide §3). `InscricaoRepository` ganha:
```java
// job de TTL: pendentes mais velhas que o corte (indice parcial idx_inscricoes_pendentes)
@Query("SELECT i FROM Inscricao i WHERE i.status = 'PENDENTE_PAGAMENTO' AND i.inscritoEm < :corte")
List<Inscricao> findPendentesExpiradas(@Param("corte") OffsetDateTime corte);
```

---

## 6. Calculo do escrow (US-040)
```java
// taxaPercentual = 0.1000 (config vigente)
valorTaxa    = valorBruto.multiply(taxaPercentual).setScale(2, RoundingMode.HALF_UP); // round(preco*0.10, 2)
valorRepasse = valorBruto.subtract(valorTaxa);                                        // bruto - taxa
// status = CONFIRMADO (retido); NENHUM movimento de repasse/reembolso e registrado no S4.
```
Exemplos: bruto 100.00 -> taxa 10.00, repasse 90.00. Bruto 99.99 -> taxa 10.00 (round HALF_UP de 9.999), repasse 89.99.
