package com.ticketeira.user.dto;

import com.ticketeira.user.domain.Papel;
import com.ticketeira.user.domain.Usuario;

import java.time.OffsetDateTime;

public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        Papel papel,
        boolean verificado,
        boolean ativo,
        OffsetDateTime criadoEm,
        /** Status do perfil de promotor (PENDENTE/VERIFICADO/REJEITADO) ou null se nao tem perfil. */
        String statusPerfil
) {
    public static UsuarioResponse from(Usuario u) {
        return from(u, null);
    }

    public static UsuarioResponse from(Usuario u, String statusPerfil) {
        return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getPapel(),
                u.isVerificado(), u.isAtivo(), u.getCriadoEm(), statusPerfil);
    }
}
