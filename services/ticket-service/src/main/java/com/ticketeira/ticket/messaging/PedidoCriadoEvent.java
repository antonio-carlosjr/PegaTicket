package com.ticketeira.ticket.messaging;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Payload publicado pelo ticket-service em afterCommit apos criar Inscricao(PENDENTE_PAGAMENTO).
 * Routing key: pedido.criado | Exchange: ticketeira.events (topic).
 * eventId e gerado na ORIGEM (aqui) e e a chave de idempotencia no payment-service.
 */
public record PedidoCriadoEvent(
        UUID eventId,               // UUID gerado pelo produtor; chave de idempotencia
        Long inscricaoId,
        Long usuarioId,
        Long eventoId,
        BigDecimal valor,           // = preco do evento (valor_bruto para escrow)
        Long promotorId,            // para repasse futuro (S5)
        OffsetDateTime ocorridoEm
) {}
