package com.ticketeira.user.service;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.user.domain.PasswordResetToken;
import com.ticketeira.user.domain.Usuario;
import com.ticketeira.user.dto.RegisterRequest;
import com.ticketeira.user.repository.PasswordResetTokenRepository;
import com.ticketeira.user.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PasswordResetServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordResetTokenRepository tokens;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private EmailService emailService;

    private Usuario ana;

    @BeforeEach
    void setUp() {
        authService.register(new RegisterRequest(
                "Ana", "ana@example.com", "senhaOriginal", null, null, null));
        ana = usuarios.findByEmail("ana@example.com").orElseThrow();
    }

    @Test
    void solicitarResetCriaTokenEEnviaEmail() {
        passwordResetService.solicitarReset("ana@example.com");

        // Captura o link que foi pro email
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(emailService, times(1)).enviarHtml(
                eq("ana@example.com"),
                anyString(),
                eq("email/password-reset"),
                varsCaptor.capture()
        );

        // O token raw vai dentro do linkReset
        String linkReset = (String) varsCaptor.getValue().get("linkReset");
        assertThat(linkReset).contains("?token=");

        // E o hash do token raw esta no banco
        assertThat(tokens.findAll()).hasSize(1);
    }

    @Test
    void solicitarResetParaEmailInexistenteNaoEnviaEmail() {
        passwordResetService.solicitarReset("naoexiste@x.com");
        verify(emailService, never()).enviarHtml(anyString(), anyString(), anyString(), anyMap());
        assertThat(tokens.findAll()).isEmpty();
    }

    @Test
    void solicitarResetInvalidaTokensAnteriores() {
        passwordResetService.solicitarReset("ana@example.com");
        passwordResetService.solicitarReset("ana@example.com");

        // Cada solicitacao gerou 1 token; mas o primeiro foi invalidado
        var todos = tokens.findAll();
        assertThat(todos).hasSize(2);
        long invalidados = todos.stream().filter(PasswordResetToken::foiUsado).count();
        assertThat(invalidados).isEqualTo(1);
    }

    @Test
    void redefinirComTokenValidoAtualizaSenha() {
        String rawToken = TokenHasher.generateRawToken();
        String hash = TokenHasher.sha256Hex(rawToken);
        tokens.save(new PasswordResetToken(ana.getId(), hash, OffsetDateTime.now().plusMinutes(30)));

        passwordResetService.redefinir(rawToken, "novaSenhaForte");

        // O hash da senha mudou
        Usuario atualizado = usuarios.findById(ana.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("novaSenhaForte", atualizado.getSenhaHash())).isTrue();
        assertThat(passwordEncoder.matches("senhaOriginal", atualizado.getSenhaHash())).isFalse();

        // E o token foi marcado como usado
        Optional<PasswordResetToken> persistido = tokens.findByTokenHash(hash);
        assertThat(persistido).isPresent();
        assertThat(persistido.get().foiUsado()).isTrue();
    }

    @Test
    void redefinirComTokenInvalidoFalha() {
        assertThatThrownBy(() -> passwordResetService.redefinir("token-inexistente", "qualquer"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalido");
    }

    @Test
    void redefinirComTokenExpiradoFalha() {
        String rawToken = TokenHasher.generateRawToken();
        String hash = TokenHasher.sha256Hex(rawToken);
        tokens.save(new PasswordResetToken(ana.getId(), hash, OffsetDateTime.now().minusMinutes(1)));

        assertThatThrownBy(() -> passwordResetService.redefinir(rawToken, "novaSenha"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("xpirado");
    }

    @Test
    void redefinirComTokenJaUsadoFalha() {
        String rawToken = TokenHasher.generateRawToken();
        String hash = TokenHasher.sha256Hex(rawToken);
        PasswordResetToken token = tokens.save(new PasswordResetToken(
                ana.getId(), hash, OffsetDateTime.now().plusMinutes(30)));
        token.marcarComoUsado();
        tokens.save(token);

        assertThatThrownBy(() -> passwordResetService.redefinir(rawToken, "novaSenha"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("utilizado");
    }
}
