package com.ticketeira.payment.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento AMQP consumido do event-service (fan-out: payment + ticket).
 * Routing key: evento.cancelado | Exchange: ticketeira.events
 * Payload conforme api-contracts §3.2.
 */
public record EventoCanceladoEvent(
        UUID eventId,
        Long eventoId,
        Long promotorId,
        OffsetDateTime ocorridoEm
) {}
