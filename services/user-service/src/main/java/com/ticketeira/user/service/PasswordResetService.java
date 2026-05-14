package com.ticketeira.user.service;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.user.domain.PasswordResetToken;
import com.ticketeira.user.domain.Usuario;
import com.ticketeira.user.repository.PasswordResetTokenRepository;
import com.ticketeira.user.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UsuarioRepository usuarios;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final String baseUrl;
    private final long ttlMinutes;

    public PasswordResetService(UsuarioRepository usuarios,
                                PasswordResetTokenRepository tokens,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService,
                                @Value("${app.password-reset.base-url}") String baseUrl,
                                @Value("${app.password-reset.ttl-minutes}") long ttlMinutes) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.baseUrl = baseUrl;
        this.ttlMinutes = ttlMinutes;
    }

    /**
     * Solicita reset de senha. Sempre retorna sucesso (mesmo se email nao existe),
     * para nao expor quais emails estao cadastrados (anti-enumeracao).
     */
    @Transactional
    public void solicitarReset(String email) {
        String emailNormalizado = email.toLowerCase().trim();
        Optional<Usuario> maybeUsuario = usuarios.findByEmail(emailNormalizado);
        if (maybeUsuario.isEmpty()) {
            log.info("Solicitacao de reset para email inexistente: {}", emailNormalizado);
            return;
        }

        Usuario usuario = maybeUsuario.get();

        // Invalida tokens ativos anteriores para evitar multiplos validos simultaneos.
        tokens.invalidarTokensAtivosDoUsuario(usuario.getId(), OffsetDateTime.now());

        String rawToken = TokenHasher.generateRawToken();
        String tokenHash = TokenHasher.sha256Hex(rawToken);
        OffsetDateTime expiraEm = OffsetDateTime.now().plusMinutes(ttlMinutes);

        tokens.save(new PasswordResetToken(usuario.getId(), tokenHash, expiraEm));

        String link = baseUrl + "?token=" + rawToken;
        Map<String, Object> vars = new HashMap<>();
        vars.put("nome", usuario.getNome());
        vars.put("linkReset", link);
        vars.put("expiraEmMinutos", ttlMinutes);

        emailService.enviarHtml(
                usuario.getEmail(),
                "Redefinicao de senha - Ticketeira",
                "email/password-reset",
                vars
        );
    }

    @Transactional
    public void redefinir(String rawToken, String novaSenha) {
        String tokenHash = TokenHasher.sha256Hex(rawToken);
        PasswordResetToken token = tokens.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException("Token de reset invalido.", 400));

        if (!token.estaValido()) {
            String motivo = token.foiUsado() ? "Token ja utilizado." : "Token expirado.";
            throw new BusinessException(motivo, 400);
        }

        Usuario usuario = usuarios.findById(token.getUsuarioId())
                .orElseThrow(() -> new BusinessException("Usuario nao encontrado.", 404));

        usuario.atualizarSenha(passwordEncoder.encode(novaSenha));
        token.marcarComoUsado();

        log.info("Senha redefinida com sucesso para usuario id={}", usuario.getId());
    }
}
