-- Controle de acesso (US-053)
ALTER TABLE usuarios ADD COLUMN ativo BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX idx_usuarios_ativo ON usuarios(ativo);

-- Perfil completo do promotor (US-052)
ALTER TABLE perfis_verificados
  ADD COLUMN email_contato   VARCHAR(160),
  ADD COLUMN cep             VARCHAR(9),
  ADD COLUMN logradouro      VARCHAR(160),
  ADD COLUMN numero          VARCHAR(20),
  ADD COLUMN complemento     VARCHAR(80),
  ADD COLUMN bairro          VARCHAR(80),
  ADD COLUMN cidade          VARCHAR(80),
  ADD COLUMN uf              CHAR(2),
  ADD COLUMN instagram       VARCHAR(80),
  ADD COLUMN website         VARCHAR(200),
  ADD COLUMN motivo_rejeicao VARCHAR(300);

ALTER TABLE perfis_verificados
  ADD CONSTRAINT chk_perfis_uf CHECK (uf IS NULL OR uf ~ '^[A-Z]{2}$');

-- Seed ADMIN idempotente
-- senha dev: Admin@123
INSERT INTO usuarios (nome, email, senha_hash, papel, verificado, ativo, criado_em)
VALUES ('Administrador', 'admin@pegaticket.local', '$2a$10$vI8aWN1Vamcg3zlL1yNdL.L/oX.aD6jG9mN/f6T5yA0l4I/U8z3Oq', 'ADMIN', TRUE, TRUE, NOW())
ON CONFLICT (email) DO NOTHING;
