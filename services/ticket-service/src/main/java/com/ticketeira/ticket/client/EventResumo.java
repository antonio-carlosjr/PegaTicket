package com.ticketeira.ticket.client;

import java.math.BigDecimal;

/**
 * Payload de validacao retornado pelo event-service (GET /internal/events/{id}, ADR-T08).
 * Campos mapeados conforme EventoInternoResponse do event-service.
 * Sprint 4: +preco (null se GRATUITO) +promotorId (para payload de pedido.criado).
 */
public record EventResumo(
        Long id,
        String titulo,
        String tipo,
        String status,
        Integer vagasDisponiveis,
        Integer capacidade,
        BigDecimal preco,       // null se GRATUITO; = valor do evento para payload AMQP
        Long promotorId         // para repasse no payload pedido.criado (S5)
) {}
