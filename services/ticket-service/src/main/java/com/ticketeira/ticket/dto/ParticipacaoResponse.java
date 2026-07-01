package com.ticketeira.ticket.dto;

/**
 * Response de GET /internal/tickets/participou (US-024 / ADR-T16).
 */
public record ParticipacaoResponse(boolean participou) {}
