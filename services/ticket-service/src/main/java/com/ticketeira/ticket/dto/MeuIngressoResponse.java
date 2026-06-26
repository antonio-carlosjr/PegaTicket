package com.ticketeira.ticket.dto;

import com.ticketeira.ticket.domain.Ingresso;
import com.ticketeira.ticket.domain.Inscricao;

import java.time.OffsetDateTime;

public record MeuIngressoResponse(
        Long ingressoId,
        String codigoUnico,
        String statusIngresso,
        Long inscricaoId,
        Long eventoId,
        String statusInscricao,
        OffsetDateTime emitidoEm
) {
    public static MeuIngressoResponse from(Ingresso ing, Inscricao ins) {
        return new MeuIngressoResponse(
                ing.getId(),
                ing.getCodigoUnico(),
                ing.getStatus().name(),
                ins.getId(),
                ins.getEventoId(),
                ins.getStatus().name(),
                ing.getEmitidoEm()
        );
    }
}
