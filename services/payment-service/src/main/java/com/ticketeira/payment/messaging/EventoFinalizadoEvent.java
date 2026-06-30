package com.ticketeira.payment.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento AMQP consumido do event-service.
 * Routing key: evento.finalizado | Exchange: ticketeira.events
 * Payload conforme api-contracts §3.1.
 */
public record EventoFinalizadoEvent(
        UUID eventId,
        Long eventoId,
        Long promotorId,
        OffsetDateTime ocorridoEm
) {}
