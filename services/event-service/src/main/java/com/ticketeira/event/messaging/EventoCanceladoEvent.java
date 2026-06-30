package com.ticketeira.event.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Payload AMQP publicado em evento.cancelado quando o promotor cancela um evento.
 * Fan-out: payment-service (fila evento.cancelado) e ticket-service (fila evento.cancelado.ticket)
 * recebem cada um sua copia via bindings independentes na mesma routing key.
 */
public record EventoCanceladoEvent(
        UUID eventId,
        Long eventoId,
        Long promotorId,
        OffsetDateTime ocorridoEm
) {}
