package com.ticketeira.ticket.dto;

import com.ticketeira.ticket.domain.Inscricao;

import java.time.OffsetDateTime;

public record InscricaoHistoricoResponse(
        Long id,
        Long eventoId,
        String status,
        OffsetDateTime inscritoEm
) {
    public static InscricaoHistoricoResponse from(Inscricao i) {
        return new InscricaoHistoricoResponse(
                i.getId(),
                i.getEventoId(),
                i.getStatus().name(),
                i.getInscritoEm()
        );
    }
}
