package com.ticketeira.user.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresInMs,
        Long userId,
        String email,
        boolean verificado
) {
    public static LoginResponse of(String token, long expiresInMs, Long userId, String email, boolean verificado) {
        return new LoginResponse(token, "Bearer", expiresInMs, userId, email, verificado);
    }
}
