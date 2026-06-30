# Sprint 5 — Trilha 5B (Experiencia) — Modelo de Dados

> Entidades JPA novas que mapeiam tabelas **ja existentes** (`checkins`, `avaliacoes`) + metodos de dominio + queries. Postgres; `ddl-auto: validate` (schema versionado por Flyway).
> **Conclusao geral: ZERO migrations Flyway.** Toda a 5B reusa schema existente. Unica mudanca: a fila `inscricao.cancelada` no `definitions.json` (infra, nao Flyway).

---

## A. ticket-service — Check-in (US-034) + Cancelamento (US-035)

### A.1 Tabela `checkins` (JA EXISTE — V1) — sem migration
```sql
-- services/ticket-service/.../V1__init.sql (existente):
CREATE TABLE checkins (
    id           BIGSERIAL   PRIMARY KEY,
    ingresso_id  BIGINT      NOT NULL UNIQUE REFERENCES ingressos(id) ON DELETE CASCADE,
    realizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valido       BOOLEAN     NOT NULL DEFAULT TRUE
);
```
> `UNIQUE(ingresso_id)` e a barreira anti-duplo-check-in. `valido` permanece `true` (sem caso de uso de invalidacao na 5B; mapeado com default).

### A.2 Entidade JPA — `Checkin` (NOVA — mapeia tabela existente)
```java
@Entity
@Table(name = "checkins")
public class Checkin {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ingresso_id", nullable = false, unique = true)
    private Long ingressoId;

    @Column(name = "realizado_em", nullable = false)
    private OffsetDateTime realizadoEm;

    @Column(nullable = false)
    private boolean valido;

    protected Checkin() {}

    /** Factory: registra o check-in de um ingresso. */
    public static Checkin de(Long ingressoId) {
        Checkin c = new Checkin();
        c.ingressoId = ingressoId;
        c.realizadoEm = OffsetDateTime.now();
        c.valido = true;
        return c;
    }
    // getters
}
```

### A.3 Entidade JPA — `Ingresso` (delta: novo metodo de check-in)
```java
/**
 * Check-in da porta (US-034): ATIVO -> UTILIZADO.
 * DIFERENTE de utilizar() (5A): aqui o 2o check-in deve FALHAR com 409
 * (criterio US-034.2), nao ser no-op idempotente.
 * - ATIVO     -> UTILIZADO (sucesso)
 * - UTILIZADO -> lanca BusinessException("INGRESSO_JA_UTILIZADO", 409)
 * - CANCELADO -> o caller ja barrou com 404 antes (ingresso invalido); defesa: lanca 409.
 */
public void realizarCheckin() {
    if (status == StatusIngresso.UTILIZADO) {
        throw new BusinessException("INGRESSO_JA_UTILIZADO", 409);
    }
    if (status == StatusIngresso.CANCELADO) {
        throw new BusinessException("INGRESSO_JA_UTILIZADO", 409); // nao reaproveitar cancelado
    }
    this.status = StatusIngresso.UTILIZADO;
}
```
> O `utilizar()` no-op idempotente da 5A **permanece** (usado por fixtures de teste da 5A: `seedInscricaoComIngressoUtilizado`). `realizarCheckin()` e o caminho de producao do US-034. A barreira atomica final no caso concorrente e o `UNIQUE(ingresso_id)` em `checkins`.

### A.4 Entidade JPA — `Inscricao` (delta: cancelamento voluntario)
```java
// Mantem cancelarPorEvento() da 5A (no-op idempotente p/ consumidor AMQP).
// NAO adicionar metodo que lanca: o cancelamento voluntario usa UPDATE condicional
// no repository (controle de concorrencia por rowsAffected), nao transicao de entidade,
// para distinguir 409 (ja cancelada) com precisao no caminho concorrente. (ver A.6)
```

### A.5 Repository — `IngressoRepository` (delta)
```java
/** Lookup do QR para check-in. Usa UNIQUE(codigo_unico) — O(1). */
Optional<Ingresso> findByCodigoUnico(String codigoUnico);

/** Ingresso de uma inscricao (para cancelar no fluxo voluntario). */
Optional<Ingresso> findByInscricaoId(Long inscricaoId);
```

### A.6 Repository — `InscricaoRepository` (delta)
```java
/**
 * Cancelamento voluntario do participante (US-035): transicao condicional.
 * Retorna 1 se cancelou (estava ATIVA|PENDENTE_PAGAMENTO), 0 se ja CANCELADA/EXPIRADA.
 * Row lock do Postgres serializa 2 cancelamentos concorrentes -> 1 vence, 0 no 2o -> 409.
 */
@Modifying(clearAutomatically = true)
@Query("UPDATE Inscricao i SET i.status = com.ticketeira.ticket.domain.StatusInscricao.CANCELADA "
     + "WHERE i.id = :id AND i.status IN ("
     + "com.ticketeira.ticket.domain.StatusInscricao.ATIVA, "
     + "com.ticketeira.ticket.domain.StatusInscricao.PENDENTE_PAGAMENTO)")
int cancelarPorParticipante(@Param("id") Long id);

/**
 * Elegibilidade de avaliacao (US-024), parte do ticket (PO-D1).
 * true sse, para usuario+evento: existe Ingresso UTILIZADO (de inscricao do usuario)
 *   OU Inscricao ATIVA. (A condicao "evento REALIZADO" e do event-service.)
 * 1 query EXISTS combinada para evitar 2 round-trips.
 */
@Query("""
        SELECT CASE WHEN COUNT(x) > 0 THEN true ELSE false END FROM (
          SELECT i.id AS x FROM Inscricao i
            WHERE i.usuarioId = :usuarioId AND i.eventoId = :eventoId
              AND i.status = com.ticketeira.ticket.domain.StatusInscricao.ATIVA
          UNION
          SELECT ing.id AS x FROM Ingresso ing
            JOIN Inscricao ins ON ing.inscricaoId = ins.id
            WHERE ins.usuarioId = :usuarioId AND ins.eventoId = :eventoId
              AND ing.status = com.ticketeira.ticket.domain.StatusIngresso.UTILIZADO
        ) ev
        """)
boolean participou(@Param("usuarioId") Long usuarioId, @Param("eventoId") Long eventoId);
```
> **Nota JPQL:** subquery em `FROM` nao e suportada por todo provider em JPQL puro. **Alternativa portavel (recomendada ao Back):** 2 `existsBy...` derivados (`existsByUsuarioIdAndEventoIdAndStatus` para ATIVA + um `@Query` EXISTS com join para o ingresso UTILIZADO) combinados com `||` no service (2 queries indexadas, ambas O(1); ainda sem N+1). O Back escolhe a forma que compila no Hibernate 6; o contrato e o boolean. Indices: `idx_inscricoes_usuario`/`idx_inscricoes_evento` (V1).

### A.7 Repository — `CheckinRepository` (NOVO)
```java
public interface CheckinRepository extends JpaRepository<Checkin, Long> {
    boolean existsByIngressoId(Long ingressoId);   // opcional (pre-check; defesa final = UNIQUE)
}
```

---

## B. event-service — Avaliacao (US-024) + Reputacao (US-025)

### B.1 Tabela `avaliacoes` (JA EXISTE — V1) — sem migration
```sql
-- services/event-service/.../V1__init.sql (existente):
CREATE TABLE avaliacoes (
    id          BIGSERIAL    PRIMARY KEY,
    evento_id   BIGINT       NOT NULL REFERENCES eventos(id) ON DELETE CASCADE,
    usuario_id  BIGINT       NOT NULL,
    nota        INTEGER      NOT NULL CHECK (nota BETWEEN 1 AND 5),
    comentario  TEXT,
    avaliado_em TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_avaliacao_usuario_evento UNIQUE (evento_id, usuario_id)
);
CREATE INDEX idx_avaliacoes_evento ON avaliacoes(evento_id);   -- ja existe
```
> `UNIQUE(evento_id,usuario_id)` -> 409 na 2a; `CHECK(nota 1-5)` = defesa em profundidade (a borda valida com `@Min/@Max`).

### B.2 Entidade JPA — `Avaliacao` (NOVA — mapeia tabela existente)
```java
@Entity
@Table(name = "avaliacoes")
public class Avaliacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false)
    private Long eventoId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private Integer nota;

    @Column(columnDefinition = "TEXT")
    private String comentario;

    @Column(name = "avaliado_em", nullable = false)
    private OffsetDateTime avaliadoEm;

    protected Avaliacao() {}

    public static Avaliacao criar(Long eventoId, Long usuarioId, Integer nota, String comentario) {
        Avaliacao a = new Avaliacao();
        a.eventoId = eventoId;
        a.usuarioId = usuarioId;
        a.nota = nota;                 // borda ja validou 1-5; CHECK e rede final
        a.comentario = comentario;
        a.avaliadoEm = OffsetDateTime.now();
        return a;
    }
    // getters
}
```

### B.3 Repository — `AvaliacaoRepository` (NOVO) + agregacao de reputacao
```java
public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {

    /**
     * Reputacao (US-025): media + total numa unica query agregada (sem N+1).
     * Sem avaliacoes -> AVG = null, COUNT = 0.
     */
    @Query("SELECT new com.ticketeira.event.dto.ReputacaoResponse(AVG(a.nota), COUNT(a)) "
         + "FROM Avaliacao a WHERE a.eventoId = :eventoId")
    ReputacaoResponse agregarReputacao(@Param("eventoId") Long eventoId);
}
```
> `ReputacaoResponse(Double media, long total)` — AVG retorna `Double` (null sem linhas). Usa `idx_avaliacoes_evento` (V1). 1 query O(1) no `detalhe()`.

### B.4 Entidade JPA — `Evento` (delta: SO o DTO interno; sem coluna nova)
```java
// Evento ja tem dataInicio (getDataInicio) e prazoReembolsoDias (getPrazoReembolsoDias).
// Nenhuma coluna nova. So EventoInternoResponse.from(e) passa a inclui-los (ver B.5).
```

### B.5 DTO — `EventoInternoResponse` (delta) + `EventoResponse` (delta) + `ReputacaoResponse` (novo)
```java
// EventoInternoResponse += dataInicio, prazoReembolsoDias (para o ticket checar prazo — US-035):
public static EventoInternoResponse from(Evento e) {
    return new EventoInternoResponse(
        e.getId(), e.getTitulo(), e.getTipo().name(), e.getStatus().name(),
        e.getVagasDisponiveis(), e.getCapacidade(), e.getPreco(), e.getPromotorId(),
        e.getDataInicio(), e.getPrazoReembolsoDias());   // NOVOS
}

// ReputacaoResponse (novo record, pacote dto):
public record ReputacaoResponse(Double media, long total) {}

// EventoResponse.from(e) ganha o parametro reputacao (injetado pelo EventService.detalhe):
// EventoResponse.from(evento, reputacao) — assinatura nova com reputacao calculada.
```

---

## C. payment-service — Reembolso individual (US-035 pago) — SEM migration

### C.1 Tabela `reembolsos` (JA EXISTE — V1) — `motivo` ja aceita CANCELAMENTO_PARTICIPANTE
```sql
-- services/payment-service/.../V1__init.sql (existente):
-- motivo VARCHAR(40) CHECK (motivo IN ('EVENTO_CANCELADO','CANCELAMENTO_PARTICIPANTE'))
-- status VARCHAR(20) CHECK (status IN ('PENDENTE','PROCESSADO','REJEITADO'))
```
> Nenhum DDL novo. `Reembolso.criar(...,'CANCELAMENTO_PARTICIPANTE')` (5A ja deixou o factory generico) grava direto.

### C.2 `processed_events` (JA EXISTE — V2) — reusado para `inscricao.cancelada`
```sql
-- event_id UUID PK, routing_key VARCHAR(80), processado_em TIMESTAMPTZ
```
> A 2a entrega de `inscricao.cancelada` colide no PK -> ACK no-op. `routing_key='inscricao.cancelada'`.

### C.3 Reuso — `Pagamento.reembolsar()` + `findByInscricaoIdForUpdate` (5A, sem mudanca)
```java
// Pagamento.reembolsar() (existente): CONFIRMADO -> REEMBOLSADO, set reembolsadoEm; false senao.
// PagamentoRepository.findByInscricaoIdForUpdate(inscricaoId) (existente): @Lock(PESSIMISTIC_WRITE).
//   pagamentos.inscricao_id e UNIQUE -> 1 pagamento por inscricao.
```

### C.4 Service — `PagamentoService` (delta: metodo individual) [OU logica no listener]
```java
/**
 * Reembolso individual por cancelamento do participante (US-035).
 * Reusa reembolsar() + Reembolso.criar; idempotencia/ack-noop ficam no listener.
 * Retorna true se reembolsou (estava CONFIRMADO), false se no-op.
 */
@Transactional
public boolean reembolsarPorInscricao(Long inscricaoId, String motivo) {
    var pagOpt = pagamentoRepository.findByInscricaoIdForUpdate(inscricaoId);
    if (pagOpt.isEmpty()) return false;            // gratuito/sem pagamento -> no-op
    Pagamento p = pagOpt.get();
    if (!p.reembolsar()) return false;             // nao-CONFIRMADO -> no-op (CR-S4-01)
    pagamentoRepository.save(p);
    reembolsoRepository.save(Reembolso.criar(p.getId(), p.getUsuarioId(), p.getValorBruto(), motivo));
    return true;
}
```
> O `InscricaoCanceladaListener` faz: `INSERT processed_events` (idempotencia) -> `reembolsarPorInscricao(inscricaoId, "CANCELAMENTO_PARTICIPANTE")`. A `@Transactional` do listener engloba ambos numa unica tx (a chamada de service participa da tx do listener; **atencao a auto-invocacao de proxy** — listener e service sao beans distintos, ok).

---

## D. infra — definitions.json (delta)

```jsonc
// queues += :
{ "name": "inscricao.cancelada", "vhost":"/", "durable":true, "auto_delete":false,
  "arguments": { "x-dead-letter-exchange":"ticketeira.dlx", "x-dead-letter-routing-key":"inscricao.cancelada" } },
{ "name": "inscricao.cancelada.dlq", "vhost":"/", "durable":true, "auto_delete":false, "arguments": {} }

// bindings += :
{ "source":"ticketeira.events", "destination":"inscricao.cancelada",     "destination_type":"queue", "routing_key":"inscricao.cancelada", "vhost":"/", "arguments":{} },
{ "source":"ticketeira.dlx",    "destination":"inscricao.cancelada.dlq", "destination_type":"queue", "routing_key":"inscricao.cancelada", "vhost":"/", "arguments":{} }
```
> Consumidor unico (payment) -> 1 fila, sem fan-out (diferente de `evento.cancelado` que tem `.ticket`). payment declara na RabbitConfig; ticket so produz (declara a exchange, ja faz).

---

## E. Config (delta) — event-service ganha outbound para o ticket
```yaml
# services/event-service/src/main/resources/application.yml += :
app:
  ticket-service:
    url: ${TICKET_SERVICE_URL:http://localhost:8083}
  internal:
    token: ${INTERNAL_TOKEN:dev-internal-secret}   # ja existe
# application-test*.yml: app.ticket-service.url + app.internal.token=test-internal-secret
```

---

## F. Resumo de migrations
| Servico | Migration | Conteudo |
|---|---|---|
| ticket | — | **nenhuma** (`checkins` existe V1; `Checkin`/`Avaliacao` mapeiam tabelas existentes; status ja suportados) |
| event | — | **nenhuma** (`avaliacoes` existe V1; `dataInicio`/`prazoReembolsoDias` ja sao colunas) |
| payment | — | **nenhuma** (`reembolsos.motivo` CHECK ja aceita `CANCELAMENTO_PARTICIPANTE`; `processed_events` existe V2) |
| infra | `definitions.json` | fila `inscricao.cancelada` + `.dlq` + bindings |

> Se o PO quiser auditoria do cancelamento voluntario (`inscricoes.cancelada_em`/`motivo`), seria uma migration **opcional** `V3__cancelamento_audit.sql` no ticket — **NAO incluida** no minimo da 5B (a saga nao precisa; status CANCELADA + extrato do payment ja registram o efeito). Decisao do PO na Fase 3.
