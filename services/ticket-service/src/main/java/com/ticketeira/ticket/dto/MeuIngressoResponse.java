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

    /**
     * Inscricao PENDENTE_PAGAMENTO ainda sem ingresso (ramo PAGO antes de pagamento.aprovado).
     * O frontend ("Meus ingressos") usa statusInscricao=PENDENTE_PAGAMENTO para exibir o card
     * "aguardando confirmacao de pagamento" com link para o checkout (US-041 criterio 5).
     * Campos de ingresso ficam vazios/nulos (codigoUnico vazio: nada de QR).
     */
    public static MeuIngressoResponse fromPendente(Inscricao ins) {
        return new MeuIngressoResponse(
                null,
                "",
                "",
                ins.getId(),
                ins.getEventoId(),
                ins.getStatus().name(),
                null
        );
    }
}
