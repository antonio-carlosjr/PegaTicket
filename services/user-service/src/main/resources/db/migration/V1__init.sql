CREATE TABLE usuarios (
    id          BIGSERIAL    PRIMARY KEY,
    nome        VARCHAR(120) NOT NULL,
    email       VARCHAR(160) NOT NULL UNIQUE,
    senha_hash  VARCHAR(120) NOT NULL,
    verificado  BOOLEAN      NOT NULL DEFAULT FALSE,
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE perfis_verificados (
    id          BIGSERIAL    PRIMARY KEY,
    usuario_id  BIGINT       NOT NULL UNIQUE REFERENCES usuarios(id) ON DELETE CASCADE,
    telefone    VARCHAR(20)  NOT NULL,
    cpf         VARCHAR(14)  NOT NULL UNIQUE,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDENTE',
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_usuarios_email ON usuarios(email);
