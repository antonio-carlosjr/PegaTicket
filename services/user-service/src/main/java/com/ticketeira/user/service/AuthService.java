package com.ticketeira.user.service;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.common.exception.UnauthorizedException;
import com.ticketeira.common.security.JwtUtil;
import com.ticketeira.user.domain.Papel;
import com.ticketeira.user.domain.PerfilVerificado;
import com.ticketeira.user.domain.Usuario;
import com.ticketeira.user.dto.LoginRequest;
import com.ticketeira.user.dto.LoginResponse;
import com.ticketeira.user.dto.RegisterRequest;
import com.ticketeira.user.dto.UsuarioResponse;
import com.ticketeira.user.repository.PerfilVerificadoRepository;
import com.ticketeira.user.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PerfilVerificadoRepository perfis;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final WelcomeEmailService welcomeEmailService;

    public AuthService(UsuarioRepository usuarios,
                       PerfilVerificadoRepository perfis,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       WelcomeEmailService welcomeEmailService) {
        this.usuarios = usuarios;
        this.perfis = perfis;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.welcomeEmailService = welcomeEmailService;
    }

    @Transactional
    public UsuarioResponse register(RegisterRequest req) {
        Papel papel = req.papelOrDefault();

        if (papel == Papel.ADMIN) {
            throw new BusinessException("Papel ADMIN nao pode ser atribuido via cadastro publico.", 403);
        }

        String email = req.email().toLowerCase().trim();
        if (usuarios.existsByEmail(email)) {
            throw new BusinessException("E-mail ja cadastrado.", 409);
        }

        String hash = passwordEncoder.encode(req.senha());
        String nome = req.nome().trim();

        Usuario novo;
        if (papel == Papel.PROMOTOR) {
            validarDadosDePromotor(req);
            novo = usuarios.save(Usuario.novoPromotorPendente(nome, email, hash));
            perfis.save(new PerfilVerificado(
                    novo.getId(), req.telefone().trim(), req.cpf().trim(),
                    req.emailContato() != null ? req.emailContato().trim() : null,
                    req.cep() != null ? req.cep().trim() : null,
                    req.logradouro() != null ? req.logradouro().trim() : null,
                    req.numero() != null ? req.numero().trim() : null,
                    req.complemento() != null ? req.complemento().trim() : null,
                    req.bairro() != null ? req.bairro().trim() : null,
                    req.cidade() != null ? req.cidade().trim() : null,
                    req.uf() != null ? req.uf().trim() : null,
                    req.instagram() != null ? req.instagram().trim() : null,
                    req.website() != null ? req.website().trim() : null
            ));
        } else {
            novo = usuarios.save(Usuario.novoParticipante(nome, email, hash));
        }

        // Envia e-mail de boas-vindas apenas apos o commit da transacao.
        // Evita disparar email se a transacao rollar back por algum motivo.
        final Usuario salvo = novo;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    welcomeEmailService.enviarBoasVindas(salvo);
                }
            });
        } else {
            welcomeEmailService.enviarBoasVindas(salvo);
        }

        return UsuarioResponse.from(novo);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest req) {
        String email = req.email().toLowerCase().trim();
        Usuario u = usuarios.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Credenciais invalidas."));

        if (!u.isAtivo()) {
            throw new UnauthorizedException("Conta inativa. Entre em contato com o administrador.");
        }

        if (!passwordEncoder.matches(req.senha(), u.getSenhaHash())) {
            throw new UnauthorizedException("Credenciais invalidas.");
        }

        String token = jwtUtil.generateToken(u.getId(), u.getEmail(), u.isVerificado(), u.getPapel().name());
        return LoginResponse.of(token, jwtUtil.getExpirationMs(), u.getId(), u.getEmail(), u.getPapel(), u.isVerificado());
    }

    private void validarDadosDePromotor(RegisterRequest req) {
        if (req.cpf() == null || req.cpf().isBlank()) {
            throw new BusinessException("CPF e obrigatorio para promotores.", 400);
        }
        if (req.telefone() == null || req.telefone().isBlank()) {
            throw new BusinessException("Telefone e obrigatorio para promotores.", 400);
        }
        if (perfis.existsByCpf(req.cpf().trim())) {
            throw new BusinessException("CPF ja cadastrado.", 409);
        }
    }
}
