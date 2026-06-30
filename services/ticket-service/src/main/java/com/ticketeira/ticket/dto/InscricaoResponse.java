package com.ticketeira.ticket.dto;

import com.ticketeira.ticket.domain.Ingresso;
import com.ticketeira.ticket.domain.Inscricao;

import java.time.OffsetDateTime;

public record InscricaoResponse(
        Long id,
        Long eventoId,
        String status,
        OffsetDateTime inscritoEm,
        IngressoResponse ingresso,                  // null quando PENDENTE_PAGAMENTO (evento PAGO)
        PagamentoPendenteResponse pagamento         // null quando GRATUITO
) {
    /** Factory para evento GRATUITO (S3) — ingresso imediato, sem referencia de pagamento. */
    public static InscricaoResponse from(Inscricao i, Ingresso ing) {
        return new InscricaoResponse(
                i.getId(),
                i.getEventoId(),
                i.getStatus().name(),
                i.getInscritoEm(),
                IngressoResponse.from(ing),
                null    // GRATUITO nao tem pagamento pendente
        );
    }

    /** Factory para evento PAGO (S4) — sem ingresso, com referencia de checkout. */
    public static InscricaoResponse fromPago(Inscricao i, PagamentoPendenteResponse pagamento) {
        return new InscricaoResponse(
                i.getId(),
                i.getEventoId(),
                i.getStatus().name(),
                i.getInscritoEm(),
                null,       // ingresso emitido somente apos pagamento.aprovado
                pagamento
        );
    }
}
