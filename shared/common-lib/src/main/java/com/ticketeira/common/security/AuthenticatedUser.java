package com.ticketeira.common.security;

public record AuthenticatedUser(
        Long id,
        String email,
        boolean verificado,
        String papel
) {
}
