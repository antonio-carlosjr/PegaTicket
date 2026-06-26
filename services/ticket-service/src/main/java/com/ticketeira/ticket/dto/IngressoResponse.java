package com.ticketeira.ticket.dto;

import com.ticketeira.ticket.domain.Ingresso;

import java.time.OffsetDateTime;

public record IngressoResponse(
        Long id,
        Long inscricaoId,
        String codigoUnico,
        String status,
        OffsetDateTime emitidoEm
) {
    public static IngressoResponse from(Ingresso ing) {
        return new IngressoResponse(
                ing.getId(),
                ing.getInscricaoId(),
                ing.getCodigoUnico(),
                ing.getStatus().name(),
                ing.getEmitidoEm()
        );
    }
}
