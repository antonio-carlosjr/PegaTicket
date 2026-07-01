package com.ticketeira.event.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request de avaliacao de evento (US-024).
 * nota: 1-5 obrigatorio; comentario: opcional, max 2000 chars.
 */
public record AvaliacaoRequest(
        @NotNull @Min(1) @Max(5) Integer nota,
        @Size(max = 2000) String comentario
) {}
