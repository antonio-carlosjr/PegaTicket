package com.ticketeira.ticket.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body para POST /tickets/checkin (US-034).
 */
public record CheckinRequest(@NotBlank String codigoUnico) {}
