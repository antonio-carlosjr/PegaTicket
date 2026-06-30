package com.ticketeira.event.dto;

import com.ticketeira.event.domain.Evento;

import java.math.BigDecimal;

/**
 * Resumo do evento para consumo INTERNO (ticket-service → event-service, ADR-T08).
 * Exposto via GET /internal/events/{id} (autorizado por X-Internal-Token), nunca pelo gateway.
 * Existe porque o detalhe publico (GET /events/{id}) exige contexto de usuario (X-User-Id),
 * indisponivel numa chamada service-to-service.
 * Sprint 4: +preco (null se GRATUITO) +promotorId (para repasse).
 */
public record EventoInternoResponse(
        Long id,
        String titulo,
        String tipo,
        String status,
        Integer vagasDisponiveis,
        Integer capacidade,
        BigDecimal preco,       // null se GRATUITO
        Long promotorId         // para repasse (S5) e payload de pedido.criado
) {
    public static EventoInternoResponse from(Evento e) {
        return new EventoInternoResponse(
                e.getId(),
                e.getTitulo(),
                e.getTipo().name(),
                e.getStatus().name(),
                e.getVagasDisponiveis(),
                e.getCapacidade(),
                e.getPreco(),
                e.getPromotorId());
    }
}
