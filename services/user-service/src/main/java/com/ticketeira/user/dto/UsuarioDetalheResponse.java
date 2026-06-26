package com.ticketeira.user.dto;

import com.ticketeira.user.domain.Papel;
import com.ticketeira.user.domain.PerfilVerificado;
import com.ticketeira.user.domain.Usuario;

import java.time.OffsetDateTime;

public record UsuarioDetalheResponse(
        Long id,
        String nome,
        String email,
        Papel papel,
        boolean verificado,
        boolean ativo,
        OffsetDateTime criadoEm,
        PerfilResponse perfil
) {
    public static UsuarioDetalheResponse from(Usuario u, PerfilVerificado p) {
        PerfilResponse pr = p != null ? new PerfilResponse(
                p.getTelefone(), p.getCpf(), p.getEmailContato(), p.getCep(),
                p.getLogradouro(), p.getNumero(), p.getComplemento(), p.getBairro(),
                p.getCidade(), p.getUf(), p.getInstagram(), p.getWebsite(), p.getStatus().name(), p.getMotivoRejeicao()
        ) : null;
        
        return new UsuarioDetalheResponse(
                u.getId(), u.getNome(), u.getEmail(), u.getPapel(),
                u.isVerificado(), u.isAtivo(), u.getCriadoEm(), pr
        );
    }
}

record PerfilResponse(
        String telefone, String cpf, String emailContato, String cep,
        String logradouro, String numero, String complemento, String bairro,
        String cidade, String uf, String instagram, String website, String status, String motivoRejeicao
) {}
