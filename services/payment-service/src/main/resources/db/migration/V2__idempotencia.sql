-- Sprint 4: idempotencia de consumidor (pedido.criado). US-060.
CREATE TABLE processed_events (
    event_id      UUID         PRIMARY KEY,
    routing_key   VARCHAR(80)  NOT NULL,
    processado_em TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
