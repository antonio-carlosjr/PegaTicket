package com.ticketeira.ticket.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento AMQP publicado pelo ticket-service quando uma inscricao e cancelada
 * pelo participante (US-035 — PAGO + dentro do prazo).
 * Routing key: inscricao.cancelada; exchange: ticketeira.events.
 * eventId (UUID gerado na origem) = chave de idempotencia (ADR-T11).
 */
public record InscricaoCanceladaEvent(
        UUID eventId,         // chave de idempotencia (gerada no publisher)
        Long inscricaoId,     // mapeia 1:1 ao pagamento (pagamentos.inscricao_id UNIQUE)
        Long usuarioId,
        Long eventoId,
        OffsetDateTime ocorridoEm
) {}
