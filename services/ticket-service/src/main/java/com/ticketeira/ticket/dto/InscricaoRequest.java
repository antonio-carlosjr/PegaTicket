package com.ticketeira.ticket.dto;

import jakarta.validation.constraints.NotNull;

public record InscricaoRequest(
        @NotNull(message = "eventoId e obrigatorio") Long eventoId
) {}
