package com.ticketeira.user.service;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.common.exception.UnauthorizedException;
import com.ticketeira.common.security.JwtUtil;
import com.ticketeira.user.domain.Usuario;
import com.ticketeira.user.dto.LoginRequest;
import com.ticketeira.user.dto.LoginResponse;
import com.ticketeira.user.dto.RegisterRequest;
import com.ticketeira.user.dto.UsuarioResponse;
import com.ticketeira.user.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UsuarioRepository repository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public UsuarioResponse register(RegisterRequest req) {
        String email = req.email().toLowerCase().trim();
        if (repository.existsByEmail(email)) {
            throw new BusinessException("E-mail ja cadastrado.", 409);
        }
        String hash = passwordEncoder.encode(req.senha());
        Usuario novo = new Usuario(req.nome().trim(), email, hash);
        return UsuarioResponse.from(repository.save(novo));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest req) {
        String email = req.email().toLowerCase().trim();
        Usuario u = repository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Credenciais invalidas."));

        if (!passwordEncoder.matches(req.senha(), u.getSenhaHash())) {
            throw new UnauthorizedException("Credenciais invalidas.");
        }

        String token = jwtUtil.generateToken(u.getId(), u.getEmail(), u.isVerificado());
        return LoginResponse.of(token, jwtUtil.getExpirationMs(), u.getId(), u.getEmail(), u.isVerificado());
    }
}
