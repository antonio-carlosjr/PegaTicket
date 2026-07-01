package com.ticketeira.ticket.client;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Payload de validacao retornado pelo event-service (GET /internal/events/{id}, ADR-T08).
 * Campos mapeados conforme EventoInternoResponse do event-service.
 * Sprint 4: +preco (null se GRATUITO) +promotorId (para payload de pedido.criado).
 * Sprint 5B: +dataInicio, +prazoReembolsoDias (para checar prazo de cancelamento, ADR-T15).
 */
public record EventResumo(
        Long id,
        String titulo,
        String tipo,
        String status,
        Integer vagasDisponiveis,
        Integer capacidade,
        BigDecimal preco,           // null se GRATUITO; = valor do evento para payload AMQP
        Long promotorId,            // para repasse no payload pedido.criado (S5)
        OffsetDateTime dataInicio,  // para checagem de prazo de cancelamento (5B, ADR-T15)
        Integer prazoReembolsoDias  // janela de cancelamento em dias; null se GRATUITO
) {}
