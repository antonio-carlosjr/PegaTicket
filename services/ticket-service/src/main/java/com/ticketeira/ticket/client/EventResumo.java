package com.ticketeira.ticket.client;

/**
 * Payload de validacao retornado pelo event-service (GET /internal/events/{id}, ADR-T08).
 * Campos mapeados conforme EventoInternoResponse do event-service.
 */
public record EventResumo(
        Long id,
        String titulo,
        String tipo,
        String status,
        Integer vagasDisponiveis,
        Integer capacidade
) {}
