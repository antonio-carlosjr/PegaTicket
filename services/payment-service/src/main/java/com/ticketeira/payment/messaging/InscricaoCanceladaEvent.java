package com.ticketeira.payment.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento AMQP consumido do ticket-service (inscricao individual cancelada pelo participante).
 * Routing key: inscricao.cancelada | Exchange: ticketeira.events
 * Payload conforme api-contracts §4.1 (5B).
 * eventId e a chave de idempotencia (ADR-T11); gerado na origem pelo ticket.
 */
public record InscricaoCanceladaEvent(
        UUID eventId,
        Long inscricaoId,
        Long usuarioId,
        Long eventoId,
        OffsetDateTime ocorridoEm
) {}
