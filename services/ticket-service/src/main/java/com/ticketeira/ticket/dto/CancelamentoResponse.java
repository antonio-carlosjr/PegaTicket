package com.ticketeira.ticket.dto;

/**
 * Response de DELETE /tickets/inscricoes/{id} (US-035).
 * reembolsoIniciado=true apenas para evento PAGO dentro do prazo (publicou inscricao.cancelada).
 */
public record CancelamentoResponse(
        Long inscricaoId,
        String status,
        boolean reembolsoIniciado
) {}
