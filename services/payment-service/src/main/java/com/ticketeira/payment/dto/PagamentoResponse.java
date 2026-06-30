package com.ticketeira.payment.dto;

import com.ticketeira.payment.domain.Pagamento;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * DTO de resposta do pagamento — campos conforme api-contracts §2.
 */
public record PagamentoResponse(
        Long id,
        Long inscricaoId,
        Long usuarioId,
        BigDecimal valorBruto,
        BigDecimal valorTaxa,
        BigDecimal valorRepasse,
        String status,
        String gateway,
        String gatewayPaymentId,
        OffsetDateTime processadoEm,
        OffsetDateTime criadoEm
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
                p.getCriadoEm()
        );
    }
}
