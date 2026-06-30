package com.ticketeira.payment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ticketeira.payment.domain.Pagamento;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * DTO de resposta do pagamento — campos conforme api-contracts §2.
 * Os valores monetarios sao serializados como STRING com 2 casas ("100.00"),
 * evitando que o JSON number perca o zero a direita (100.0) e mantendo a precisao
 * de dinheiro na API.
 * eventoId/promotorId: additive (TECH-S4-01 / RA2) — null em pagamentos legados.
 */
public record PagamentoResponse(
        Long id,
        Long inscricaoId,
        Long usuarioId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valorBruto,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valorTaxa,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valorRepasse,
        String status,
        String gateway,
        String gatewayPaymentId,
        OffsetDateTime processadoEm,
        OffsetDateTime criadoEm,
        Long eventoId,
        Long promotorId
) {
    public static PagamentoResponse from(Pagamento p) {
        return new PagamentoResponse(
                p.getId(),
                p.getInscricaoId(),
                p.getUsuarioId(),
                p.getValorBruto(),
                p.getValorTaxa(),
                p.getValorRepasse(),
                p.getStatus().name(),
                p.getGateway(),
                p.getGatewayPaymentId(),
                p.getProcessadoEm(),
                p.getCriadoEm(),
                p.getEventoId(),
                p.getPromotorId()
        );
    }
}
