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

CREATE TABLE checkins (
    id             BIGSERIAL   PRIMARY KEY,
    ingresso_id    BIGINT      NOT NULL UNIQUE REFERENCES ingressos(id) ON DELETE CASCADE,
    realizado_em   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valido         BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_inscricoes_usuario  ON inscricoes(usuario_id);
CREATE INDEX idx_inscricoes_evento   ON inscricoes(evento_id);
CREATE INDEX idx_ingressos_status    ON ingressos(status);
