package com.ticketeira.user.service;

import com.ticketeira.common.exception.NotFoundException;
import com.ticketeira.user.domain.Usuario;
import com.ticketeira.user.dto.UsuarioResponse;
import com.ticketeira.user.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UsuarioRepository repository;

    public UserService(UsuarioRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public UsuarioResponse findById(Long id) {
        Usuario u = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario nao encontrado."));
        return UsuarioResponse.from(u);
    }

    @Transactional
    public UsuarioResponse verificar(Long id) {
        Usuario u = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario nao encontrado."));
        u.marcarComoVerificado();
        return UsuarioResponse.from(repository.save(u));
    }
}
