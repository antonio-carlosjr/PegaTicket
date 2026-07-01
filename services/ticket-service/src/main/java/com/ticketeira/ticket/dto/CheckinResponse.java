package com.ticketeira.ticket.dto;

import com.ticketeira.ticket.domain.Checkin;
import com.ticketeira.ticket.domain.Ingresso;

import java.time.OffsetDateTime;

/**
 * Response de POST /tickets/checkin (US-034).
 */
public record CheckinResponse(
        Long ingressoId,
        Long inscricaoId,
        String status,
        OffsetDateTime realizadoEm
) {
    public static CheckinResponse from(Ingresso ingresso, Checkin checkin) {
        return new CheckinResponse(
                ingresso.getId(),
                ingresso.getInscricaoId(),
                ingresso.getStatus().name(),
                checkin.getRealizadoEm()
        );
    }
}
