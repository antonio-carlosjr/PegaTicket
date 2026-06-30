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
