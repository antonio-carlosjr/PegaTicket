package com.ticketeira.user.service;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.user.domain.PerfilVerificado;
import com.ticketeira.user.dto.RegisterRequest;
import com.ticketeira.user.repository.PerfilVerificadoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotorService {

    private final PerfilVerificadoRepository perfis;

    public PromotorService(PerfilVerificadoRepository perfis) {
        this.perfis = perfis;
    }

    @Transactional
    public void solicitarOuReenviar(Long usuarioId, RegisterRequest req) {
        if (req.cpf() == null || req.cpf().isBlank() || req.telefone() == null || req.telefone().isBlank()) {
            throw new BusinessException("CPF e Telefone são obrigatórios para promotores.", 400);
        }

        PerfilVerificado perfil = perfis.findByUsuarioId(usuarioId).orElse(null);

        if (perfil != null) {
            if (perfil.getCpf() != null && !perfil.getCpf().equals(req.cpf()) && perfis.existsByCpf(req.cpf())) {
                throw new BusinessException("CPF ja cadastrado.", 409);
            }
            perfil.atualizar(req.telefone().trim(), req.cpf().trim(),
                    req.emailContato() != null ? req.emailContato().trim() : null,
                    req.cep() != null ? req.cep().trim() : null,
                    req.logradouro() != null ? req.logradouro().trim() : null,
                    req.numero() != null ? req.numero().trim() : null,
                    req.complemento() != null ? req.complemento().trim() : null,
                    req.bairro() != null ? req.bairro().trim() : null,
                    req.cidade() != null ? req.cidade().trim() : null,
                    req.uf() != null ? req.uf().trim() : null,
                    req.instagram() != null ? req.instagram().trim() : null,
                    req.website() != null ? req.website().trim() : null);
            perfil.reenviar();
            perfis.save(perfil);
        } else {
            if (perfis.existsByCpf(req.cpf().trim())) {
                throw new BusinessException("CPF ja cadastrado.", 409);
            }
            PerfilVerificado novo = new PerfilVerificado(usuarioId, req.telefone().trim(), req.cpf().trim(),
                    req.emailContato() != null ? req.emailContato().trim() : null,
                    req.cep() != null ? req.cep().trim() : null,
                    req.logradouro() != null ? req.logradouro().trim() : null,
                    req.numero() != null ? req.numero().trim() : null,
                    req.complemento() != null ? req.complemento().trim() : null,
                    req.bairro() != null ? req.bairro().trim() : null,
                    req.cidade() != null ? req.cidade().trim() : null,
                    req.uf() != null ? req.uf().trim() : null,
                    req.instagram() != null ? req.instagram().trim() : null,
                    req.website() != null ? req.website().trim() : null);
            perfis.save(novo);
        }
    }
}
