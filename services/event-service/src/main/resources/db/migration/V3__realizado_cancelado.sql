-- Sprint 5A: carimbos das transicoes que disparam a saga financeira (auditoria).
ALTER TABLE eventos ADD COLUMN realizado_em  TIMESTAMPTZ;
ALTER TABLE eventos ADD COLUMN cancelado_em  TIMESTAMPTZ;
