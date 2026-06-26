package com.ticketeira.ticket.dto;

import com.ticketeira.ticket.domain.Ingresso;
import com.ticketeira.ticket.domain.Inscricao;

import java.time.OffsetDateTime;

public record InscricaoResponse(
        Long id,
        Long eventoId,
        String status,
        OffsetDateTime inscritoEm,
        IngressoResponse ingresso
) {
    public static InscricaoResponse from(Inscricao i, Ingresso ing) {
        return new InscricaoResponse(
                i.getId(),
                i.getEventoId(),
                i.getStatus().name(),
                i.getInscritoEm(),
                IngressoResponse.from(ing)
        );
    }
}
