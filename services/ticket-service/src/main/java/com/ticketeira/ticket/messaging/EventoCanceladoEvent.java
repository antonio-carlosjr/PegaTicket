package com.ticketeira.ticket.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Payload consumido pelo ticket-service ao receber evento.cancelado do event-service (fan-out).
 * Fila dedicada: evento.cancelado.ticket (rk: evento.cancelado, exchange: ticketeira.events).
 * eventId e a chave de idempotencia (processed_events PK).
 * Campos identicos ao contrato do api-contracts.md §3.2.
 */
public record EventoCanceladoEvent(
        UUID eventId,
        Long eventoId,
        Long promotorId,
        OffsetDateTime ocorridoEm
) {}
