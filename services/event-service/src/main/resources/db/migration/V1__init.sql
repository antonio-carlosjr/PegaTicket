CREATE TABLE eventos (
    id           BIGSERIAL    PRIMARY KEY,
    titulo       VARCHAR(160) NOT NULL,
    descricao    TEXT,
    data_inicio  TIMESTAMPTZ  NOT NULL,
    data_fim     TIMESTAMPTZ  NOT NULL,
    local        VARCHAR(200) NOT NULL,
    tipo         VARCHAR(20)  NOT NULL CHECK (tipo IN ('GRATUITO','PAGO')),
    status       VARCHAR(20)  NOT NULL DEFAULT 'RASCUNHO' CHECK (status IN ('RASCUNHO','PUBLICADO','REALIZADO','CANCELADO')),
    capacidade   INTEGER      NOT NULL CHECK (capacidade > 0),
    preco        NUMERIC(12,2),
    prazo_reembolso_dias INTEGER,
    promotor_id  BIGINT       NOT NULL,
    criado_em    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_datas CHECK (data_fim >= data_inicio),
    CONSTRAINT chk_preco_pago CHECK (
        (tipo = 'PAGO' AND preco IS NOT NULL AND preco > 0 AND prazo_reembolso_dias IS NOT NULL)
        OR (tipo = 'GRATUITO' AND preco IS NULL)
    )
);

CREATE TABLE avaliacoes (
    id          BIGSERIAL    PRIMARY KEY,
    evento_id   BIGINT       NOT NULL REFERENCES eventos(id) ON DELETE CASCADE,
    usuario_id  BIGINT       NOT NULL,
    nota        INTEGER      NOT NULL CHECK (nota BETWEEN 1 AND 5),
    comentario  TEXT,
    avaliado_em TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_avaliacao_usuario_evento UNIQUE (evento_id, usuario_id)
);

CREATE INDEX idx_eventos_promotor ON eventos(promotor_id);
CREATE INDEX idx_eventos_status   ON eventos(status);
CREATE INDEX idx_avaliacoes_evento ON avaliacoes(evento_id);
