# Sprint 5 — Trilha 5A (Financeiro) — Modelo de Dados

> Migrations Flyway + deltas de entidade JPA. Postgres; `ddl-auto: validate` (schema versionado, nunca gerado pelo Hibernate). Colunas `snake_case`; `TIMESTAMPTZ`.

---

## A. payment-service — migration V3 (TECH-S4-01 + repasse/reembolso)

`services/payment-service/src/main/resources/db/migration/V3__repasse_reembolso.sql`
```sql
-- Sprint 5A: TECH-S4-01 (evento_id/promotor_id) + suporte a repasse/reembolso.

-- 1) Persistir o vinculo do pagamento com o evento e o promotor.
--    Necessario para o repasse saber QUAIS pagamentos (evento_id) e PARA QUEM (promotor_id).
--    NULLABLE: pagamentos legados (pre-V3) ficam null; novos sao populados via pedido.criado
--    (que ja carrega eventoId/promotorId no payload desde o S4).
ALTER TABLE pagamentos ADD COLUMN evento_id   BIGINT;
ALTER TABLE pagamentos ADD COLUMN promotor_id BIGINT;

-- 2) Carimbos de transicao financeira (auditoria/extrato).
ALTER TABLE pagamentos ADD COLUMN repassado_em   TIMESTAMPTZ;
ALTER TABLE pagamentos ADD COLUMN reembolsado_em TIMESTAMPTZ;

-- 3) Indice para o filtro do repasse/reembolso: WHERE evento_id=? AND status='CONFIRMADO'.
--    (status ja tem idx_pagamentos_status; este cobre o evento_id.)
CREATE INDEX idx_pagamentos_evento ON pagamentos(evento_id);

-- 4) (OPCIONAL — defesa extra anti-reembolso-duplicado; PO decide, ver architecture §Idempotencia)
--    Barra 2 reembolsos EVENTO_CANCELADO para o mesmo pagamento mesmo com eventId diferente.
-- CREATE UNIQUE INDEX uk_reembolso_evento_cancelado
--     ON reembolsos(pagamento_id) WHERE motivo = 'EVENTO_CANCELADO';
```
> Destrutiva? Nao. Apenas adiciona colunas NULLABLE + indices. Revertivel (drop colunas/indices).

### Entidade JPA — `Pagamento` (delta)
```java
@Column(name = "evento_id")    private Long eventoId;     // NULLABLE (legado)
@Column(name = "promotor_id")  private Long promotorId;
@Column(name = "repassado_em")    private OffsetDateTime repassadoEm;
@Column(name = "reembolsado_em")  private OffsetDateTime reembolsadoEm;

// factory ganha os 2 ids (vindos do PedidoCriadoEvent, que ja os carrega):
public static Pagamento pendente(Long inscricaoId, Long usuarioId, Long eventoId, Long promotorId,
                                 BigDecimal valorBruto, BigDecimal taxaPercentual) { ... p.eventoId=eventoId; p.promotorId=promotorId; ... }

/** CONFIRMADO -> REPASSADO. Idempotente/condicional: retorna false se nao estava CONFIRMADO. */
public boolean repassar() {
    if (this.status != StatusPagamento.CONFIRMADO) return false;
    this.status = StatusPagamento.REPASSADO;
    this.repassadoEm = OffsetDateTime.now();
    return true;
}
/** CONFIRMADO -> REEMBOLSADO. Idempotente/condicional: retorna false se nao estava CONFIRMADO. */
public boolean reembolsar() {
    if (this.status != StatusPagamento.CONFIRMADO) return false;
    this.status = StatusPagamento.REEMBOLSADO;
    this.reembolsadoEm = OffsetDateTime.now();
    return true;
}
```
> `criarPendente(PedidoCriadoEvent)` passa `evento.eventoId()` e `evento.promotorId()` ao factory (o payload **ja os contem** — nenhum produtor muda).

### Entidade JPA — `Reembolso` (passa a ser ESCRITO)
```java
// + factory (hoje a classe so tem getters):
public static Reembolso criar(Long pagamentoId, Long usuarioId, BigDecimal valor, String motivo) {
    Reembolso r = new Reembolso();
    r.pagamentoId = pagamentoId;
    r.usuarioId   = usuarioId;
    r.valor       = valor.setScale(2, RoundingMode.HALF_UP);  // dinheiro: escala 2
    r.motivo      = motivo;            // 'EVENTO_CANCELADO' (5A) | 'CANCELAMENTO_PARTICIPANTE' (5B)
    r.status      = "PROCESSADO";      // reembolso simulado e imediato (sem gateway real)
    r.solicitadoEm = OffsetDateTime.now();
    r.processadoEm = OffsetDateTime.now();
    return r;
}
```
> `motivo`/`status` ja tem CHECK no V1 (`motivo IN ('EVENTO_CANCELADO','CANCELAMENTO_PARTICIPANTE')`, `status IN ('PENDENTE','PROCESSADO','REJEITADO')`). Sem migration para isso.

### Repository — `PagamentoRepository` (delta)
```java
// Repasse em massa (1 UPDATE condicional — O(1) round-trips):
@Modifying(clearAutomatically = true)
@Query("UPDATE Pagamento p SET p.status = com.ticketeira.payment.domain.StatusPagamento.REPASSADO, "
     + "p.repassadoEm = :agora "
     + "WHERE p.eventoId = :eventoId AND p.status = com.ticketeira.payment.domain.StatusPagamento.CONFIRMADO")
int repassarConfirmadosDoEvento(@Param("eventoId") Long eventoId, @Param("agora") OffsetDateTime agora);

// Reembolso: carrega os CONFIRMADO com lock para inserir 1 reembolso por linha.
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Pagamento p WHERE p.eventoId = :eventoId AND p.status = com.ticketeira.payment.domain.StatusPagamento.CONFIRMADO")
List<Pagamento> findConfirmadosDoEventoForUpdate(@Param("eventoId") Long eventoId);
```
> `ProcessedEvent`/`processed_events` (V2) reusados sem mudanca (`event_id UUID PK`). Tabela e repository ja existem.

---

## B. event-service — migration V3 (carimbos de transicao)

`services/event-service/src/main/resources/db/migration/V3__realizado_cancelado.sql`
```sql
-- Sprint 5A: carimbos das transicoes que disparam a saga financeira (auditoria).
ALTER TABLE eventos ADD COLUMN realizado_em  TIMESTAMPTZ;
ALTER TABLE eventos ADD COLUMN cancelado_em  TIMESTAMPTZ;
```
> Estritamente opcionais para o fluxo (a saga so precisa do status + evento AMQP), mas baratos e uteis para extrato/auditoria e para um futuro job de finalizacao (`data_fim < now → REALIZADO`). event-service **nao** ganha `processed_events` (so produz, nao consome).

### Entidade JPA — `Evento` (delta)
```java
@Column(name = "realizado_em")  private OffsetDateTime realizadoEm;
@Column(name = "cancelado_em")  private OffsetDateTime canceladoEm;

/** PUBLICADO -> REALIZADO (gatilho do repasse). Guard espelha cancelar()/publicar(). */
public void realizar() {
    if (status == StatusEvento.REALIZADO) throw new BusinessException("EVENTO_JA_REALIZADO", 409);
    if (status != StatusEvento.PUBLICADO) throw new BusinessException("TRANSICAO_INVALIDA", 409);
    this.status = StatusEvento.REALIZADO;
    this.realizadoEm = OffsetDateTime.now();
    this.atualizadoEm = OffsetDateTime.now();
}

// cancelar() ganha 1 linha (set canceladoEm; e — se PO aprovar — reset de vagas):
public void cancelar() {
    // ... guard existente ...
    this.status = StatusEvento.CANCELADO;
    this.canceladoEm = OffsetDateTime.now();
    // PO-opt: this.vagasDisponiveis = this.capacidade;  // banco reflete "vagas voltam a capacidade"
    this.atualizadoEm = OffsetDateTime.now();
}
```

---

## C. ticket-service — SEM migration

`inscricoes.status` ja aceita `CANCELADA` (V1 CHECK) e `ingressos.status` ja aceita `CANCELADO` (V1 CHECK). `processed_events` ja existe (V2). `idx_inscricoes_evento` ja existe (V1). **Nenhuma migration nova.**

### Entidade JPA — `Inscricao` / `Ingresso` (delta — so metodos)
```java
// Inscricao:
/** ATIVA|PENDENTE_PAGAMENTO -> CANCELADA. Idempotente: no-op se ja CANCELADA/EXPIRADA. */
public boolean cancelarPorEvento() {
    if (status == StatusInscricao.ATIVA || status == StatusInscricao.PENDENTE_PAGAMENTO) {
        this.status = StatusInscricao.CANCELADA;
        return true;
    }
    return false;  // CANCELADA/EXPIRADA: no-op (idempotente, sem lancar — evita poison message)
}

// Ingresso:
/** ATIVO -> CANCELADO. Idempotente: no-op se ja UTILIZADO/CANCELADO. */
public boolean cancelar() {
    if (status == StatusIngresso.ATIVO) { this.status = StatusIngresso.CANCELADO; return true; }
    return false;
}
```

### Repository — `InscricaoRepository` / `IngressoRepository` (delta)
```java
// Inscricao em massa por evento (UPDATE condicional — usa idx_inscricoes_evento):
@Modifying(clearAutomatically = true)
@Query("UPDATE Inscricao i SET i.status = com.ticketeira.ticket.domain.StatusInscricao.CANCELADA "
     + "WHERE i.eventoId = :eventoId AND i.status IN (com.ticketeira.ticket.domain.StatusInscricao.ATIVA, "
     + "com.ticketeira.ticket.domain.StatusInscricao.PENDENTE_PAGAMENTO)")
int cancelarInscricoesDoEvento(@Param("eventoId") Long eventoId);

// Ingressos ATIVO do evento -> CANCELADO (join via inscricao_id):
@Modifying(clearAutomatically = true)
@Query("UPDATE Ingresso ing SET ing.status = com.ticketeira.ticket.domain.StatusIngresso.CANCELADO "
     + "WHERE ing.status = com.ticketeira.ticket.domain.StatusIngresso.ATIVO "
     + "AND ing.inscricaoId IN (SELECT i.id FROM Inscricao i WHERE i.eventoId = :eventoId)")
int cancelarIngressosDoEvento(@Param("eventoId") Long eventoId);
```
> Ordem no listener: cancelar ingressos **antes** das inscricoes (ou indiferente — sao por evento_id/inscricao_id, nao ha FK quebrando), dentro da mesma tx do `INSERT processed_events`.

---

## D. infra — definitions.json (delta)

Adicionar (alem do que ja existe): exchange/filas/bindings para `evento.cancelado`.
```jsonc
// queues += :
{ "name": "evento.cancelado",        "vhost":"/", "durable":true, "auto_delete":false,
  "arguments": { "x-dead-letter-exchange":"ticketeira.dlx", "x-dead-letter-routing-key":"evento.cancelado" } },
{ "name": "evento.cancelado.dlq",    "vhost":"/", "durable":true, "auto_delete":false, "arguments": {} },
{ "name": "evento.cancelado.ticket", "vhost":"/", "durable":true, "auto_delete":false,
  "arguments": { "x-dead-letter-exchange":"ticketeira.dlx", "x-dead-letter-routing-key":"evento.cancelado.ticket" } },
{ "name": "evento.cancelado.ticket.dlq", "vhost":"/", "durable":true, "auto_delete":false, "arguments": {} }

// bindings += :
{ "source":"ticketeira.events", "destination":"evento.cancelado",        "destination_type":"queue", "routing_key":"evento.cancelado",        "vhost":"/", "arguments":{} },
{ "source":"ticketeira.events", "destination":"evento.cancelado.ticket", "destination_type":"queue", "routing_key":"evento.cancelado",        "vhost":"/", "arguments":{} },
{ "source":"ticketeira.dlx",    "destination":"evento.cancelado.dlq",        "destination_type":"queue", "routing_key":"evento.cancelado",        "vhost":"/", "arguments":{} },
{ "source":"ticketeira.dlx",    "destination":"evento.cancelado.ticket.dlq", "destination_type":"queue", "routing_key":"evento.cancelado.ticket", "vhost":"/", "arguments":{} }
```
> `evento.finalizado` + `evento.finalizado.dlq` JA existem. Os mesmos beans devem ser declarados na RabbitConfig de cada servico (RabbitAdmin idempotente; as filas tambem existem via definitions.json importado pelo container de teste).

---

## E. Resumo de migrations (nomes/colunas)
| Servico | Migration | Colunas/objetos |
|---|---|---|
| payment | `V3__repasse_reembolso.sql` | `pagamentos += evento_id BIGINT, promotor_id BIGINT, repassado_em TIMESTAMPTZ, reembolsado_em TIMESTAMPTZ`; `idx_pagamentos_evento`; (opt) `uk_reembolso_evento_cancelado` |
| event | `V3__realizado_cancelado.sql` | `eventos += realizado_em TIMESTAMPTZ, cancelado_em TIMESTAMPTZ` |
| ticket | — | nenhuma (status ja existem; processed_events ja existe) |
| infra | `definitions.json` | filas/bindings `evento.cancelado` (+`.ticket`) + DLQs |
