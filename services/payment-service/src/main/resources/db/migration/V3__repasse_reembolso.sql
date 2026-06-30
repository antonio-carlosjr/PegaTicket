-- Sprint 5A: TECH-S4-01 (evento_id/promotor_id) + suporte a repasse/reembolso em massa.

-- 1) Vinculo do pagamento com o evento e o promotor.
--    NULLABLE: pagamentos legados (pre-V3) ficam null; novos populados via pedido.criado.
ALTER TABLE pagamentos ADD COLUMN evento_id   BIGINT;
ALTER TABLE pagamentos ADD COLUMN promotor_id BIGINT;

-- 2) Carimbos de transicao financeira (auditoria/extrato).
ALTER TABLE pagamentos ADD COLUMN repassado_em   TIMESTAMPTZ;
ALTER TABLE pagamentos ADD COLUMN reembolsado_em TIMESTAMPTZ;

-- 3) Indice para o filtro: WHERE evento_id=? AND status='CONFIRMADO'.
CREATE INDEX idx_pagamentos_evento ON pagamentos(evento_id);

-- 4) Defesa extra anti-reembolso-duplicado (PO aprovou — RA1).
--    Barra 2 reembolsos EVENTO_CANCELADO para o mesmo pagamento mesmo com eventId diferente.
CREATE UNIQUE INDEX uk_reembolso_evento_cancelado
    ON reembolsos(pagamento_id) WHERE motivo = 'EVENTO_CANCELADO';
