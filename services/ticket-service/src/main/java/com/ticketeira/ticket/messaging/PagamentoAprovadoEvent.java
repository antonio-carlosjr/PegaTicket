package com.ticketeira.ticket.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Payload consumido pelo ticket-service ao receber pagamento.aprovado do payment-service.
 * Routing key: pagamento.aprovado | Exchange: ticketeira.events (topic).
 * eventId e a chave de idempotencia (processada via processed_events PK).
 */
public record PagamentoAprovadoEvent(
        UUID eventId,           // gerado no payment-service; chave de idempotencia
        Long pagamentoId,
        Long inscricaoId,
        Long usuarioId,
        Long eventoId,
        OffsetDateTime ocorridoEm
) {}
