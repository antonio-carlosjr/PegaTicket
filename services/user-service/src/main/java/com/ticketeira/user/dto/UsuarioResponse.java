package com.ticketeira.user.dto;

import com.ticketeira.user.domain.Usuario;

import java.time.OffsetDateTime;

public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        boolean verificado,
        OffsetDateTime criadoEm
) {
    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.isVerificado(), u.getCriadoEm());
    }
}
