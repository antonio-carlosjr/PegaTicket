package com.ticketeira.event.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Payload AMQP publicado em evento.finalizado quando o promotor encerra um evento
 * (PUBLICADO → REALIZADO). O eventId UUID gerado na origem e a chave de idempotencia
 * para consumidores (ADR-T11).
 */
public record EventoFinalizadoEvent(
        UUID eventId,
        Long eventoId,
        Long promotorId,
        OffsetDateTime ocorridoEm
) {}
