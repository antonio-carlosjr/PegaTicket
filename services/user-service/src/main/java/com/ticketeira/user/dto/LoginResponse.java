package com.ticketeira.user.dto;

import com.ticketeira.user.domain.Papel;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresInMs,
        Long userId,
        String email,
        Papel papel,
        boolean verificado
) {
    public static LoginResponse of(String token, long expiresInMs, Long userId, String email, Papel papel, boolean verificado) {
        return new LoginResponse(token, "Bearer", expiresInMs, userId, email, papel, verificado);
    }
}
