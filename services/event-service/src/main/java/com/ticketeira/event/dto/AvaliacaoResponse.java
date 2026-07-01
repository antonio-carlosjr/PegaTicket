package com.ticketeira.event.dto;

import com.ticketeira.event.domain.Avaliacao;

import java.time.OffsetDateTime;

/**
 * Response de avaliacao criada (US-024).
 */
public record AvaliacaoResponse(
        Long id,
        Long eventoId,
        Long usuarioId,
        Integer nota,
        String comentario,
        OffsetDateTime avaliadoEm
) {
    public static AvaliacaoResponse from(Avaliacao a) {
        return new AvaliacaoResponse(
                a.getId(),
                a.getEventoId(),
                a.getUsuarioId(),
                a.getNota(),
                a.getComentario(),
                a.getAvaliadoEm());
    }
}
