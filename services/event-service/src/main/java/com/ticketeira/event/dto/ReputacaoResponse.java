package com.ticketeira.event.dto;

/**
 * Reputacao agregada de um evento: media de notas + total de avaliacoes (US-025).
 * media = null quando total = 0 (sem avaliacoes).
 */
public record ReputacaoResponse(Double media, long total) {}
