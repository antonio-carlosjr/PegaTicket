-- ============================================================
-- V2: Adiciona papel ao usuario e cria tabela de tokens de
-- reset de senha. Mantem compatibilidade com V1 (usuarios pre-
-- existentes viram PARTICIPANTE verificado).
-- ============================================================

ALTER TABLE usuarios ADD COLUMN papel VARCHAR(20) NOT NULL DEFAULT 'PARTICIPANTE';
ALTER TABLE usuarios ADD CONSTRAINT chk_usuarios_papel
    CHECK (papel IN ('PARTICIPANTE','PROMOTOR','ADMIN'));

-- Participantes nao precisam de verificacao; promotores comecam pendentes.
UPDATE usuarios SET verificado = TRUE WHERE papel = 'PARTICIPANTE';

CREATE INDEX idx_usuarios_papel ON usuarios(papel);

-- Tokens de reset de senha (hash SHA-256 para lookup direto).
CREATE TABLE password_reset_tokens (
    id          BIGSERIAL    PRIMARY KEY,
    usuario_id  BIGINT       NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    token_hash  CHAR(64)     NOT NULL UNIQUE,
    expira_em   TIMESTAMPTZ  NOT NULL,
    usado_em    TIMESTAMPTZ,
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_reset_tokens_usuario ON password_reset_tokens(usuario_id);
CREATE INDEX idx_password_reset_tokens_ativo ON password_reset_tokens(expira_em)
    WHERE usado_em IS NULL;
