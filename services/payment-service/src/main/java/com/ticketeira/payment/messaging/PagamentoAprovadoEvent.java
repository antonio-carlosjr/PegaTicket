package com.ticketeira.payment.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento AMQP publicado apos confirmacao do pagamento.
 * Routing key: pagamento.aprovado | Exchange: ticketeira.events
 * Conforme api-contracts §7.2.
 */
public record PagamentoAprovadoEvent(
        UUID eventId,
        Long pagamentoId,
        Long inscricaoId,
        Long usuarioId,
        Long eventoId,
        OffsetDateTime ocorridoEm
) {}
