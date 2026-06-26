package com.ticketeira.event.dto;

import com.ticketeira.event.domain.Evento;

/**
 * Resumo do evento para consumo INTERNO (ticket-service → event-service, ADR-T08).
 * Exposto via GET /internal/events/{id} (autorizado por X-Internal-Token), nunca pelo gateway.
 * Existe porque o detalhe publico (GET /events/{id}) exige contexto de usuario (X-User-Id),
 * indisponivel numa chamada service-to-service.
 */
public record EventoInternoResponse(
        Long id,
        String titulo,
        String tipo,
        String status,
        Integer vagasDisponiveis,
        Integer capacidade
) {
    public static EventoInternoResponse from(Evento e) {
        return new EventoInternoResponse(
                e.getId(),
                e.getTitulo(),
                e.getTipo().name(),
                e.getStatus().name(),
                e.getVagasDisponiveis(),
                e.getCapacidade());
    }
}
