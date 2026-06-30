package com.ticketeira.payment.messaging;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento AMQP consumido do ticket-service.
 * Routing key: pedido.criado | Exchange: ticketeira.events
 * Conforme api-contracts §7.1.
 */
public record PedidoCriadoEvent(
        UUID eventId,
        Long inscricaoId,
        Long usuarioId,
        Long eventoId,
        BigDecimal valor,
        Long promotorId,
        OffsetDateTime ocorridoEm
) {}
