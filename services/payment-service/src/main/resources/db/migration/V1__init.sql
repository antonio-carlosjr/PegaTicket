CREATE TABLE configuracao_plataforma (
    id                BIGSERIAL    PRIMARY KEY,
    taxa_percentual   NUMERIC(5,4) NOT NULL CHECK (taxa_percentual >= 0 AND taxa_percentual <= 1),
    vigente_desde     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO configuracao_plataforma (taxa_percentual) VALUES (0.1000);

CREATE TABLE pagamentos (
    id                  BIGSERIAL    PRIMARY KEY,
    inscricao_id        BIGINT       NOT NULL UNIQUE,
    usuario_id          BIGINT       NOT NULL,
    valor_bruto         NUMERIC(12,2) NOT NULL CHECK (valor_bruto >= 0),
    valor_taxa          NUMERIC(12,2) NOT NULL DEFAULT 0,
    valor_repasse       NUMERIC(12,2) NOT NULL DEFAULT 0,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE','CONFIRMADO','REEMBOLSADO','REPASSADO')),
    gateway             VARCHAR(50)  NOT NULL DEFAULT 'SIMULADO',
    gateway_payment_id  VARCHAR(80),
    processado_em       TIMESTAMPTZ,
    criado_em           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE reembolsos (
    id              BIGSERIAL    PRIMARY KEY,
    pagamento_id    BIGINT       NOT NULL REFERENCES pagamentos(id) ON DELETE CASCADE,
    usuario_id      BIGINT       NOT NULL,
    valor           NUMERIC(12,2) NOT NULL CHECK (valor >= 0),
    motivo          VARCHAR(40)  NOT NULL CHECK (motivo IN ('EVENTO_CANCELADO','CANCELAMENTO_PARTICIPANTE')),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE','PROCESSADO','REJEITADO')),
    solicitado_em   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processado_em   TIMESTAMPTZ
);

CREATE INDEX idx_pagamentos_usuario   ON pagamentos(usuario_id);
CREATE INDEX idx_pagamentos_status    ON pagamentos(status);
CREATE INDEX idx_reembolsos_pagamento ON reembolsos(pagamento_id);
