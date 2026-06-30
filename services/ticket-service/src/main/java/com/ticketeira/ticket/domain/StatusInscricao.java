package com.ticketeira.ticket.domain;

public enum StatusInscricao {
    ATIVA,
    CANCELADA,
    PENDENTE_PAGAMENTO,   // Sprint 4: vaga reservada, pagamento ainda nao confirmado
    EXPIRADA              // Sprint 4: TTL expirou, vaga liberada (ADR-T10)
}
