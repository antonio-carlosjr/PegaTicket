package com.ticketeira.common.security;

import com.ticketeira.common.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "este-e-um-segredo-de-teste-com-mais-de-32-chars-ok";
    private static final long EXP_MS = 60_000L;

    private final JwtUtil jwt = new JwtUtil(SECRET, EXP_MS);

    @Test
    void deveGerarEValidarTokenComClaims() {
        String token = jwt.generateToken(42L, "ana@example.com", true);

        AuthenticatedUser user = jwt.validateToken(token);

        assertThat(user.id()).isEqualTo(42L);
        assertThat(user.email()).isEqualTo("ana@example.com");
        assertThat(user.verificado()).isTrue();
        assertThat(user.papel()).isEqualTo("PARTICIPANTE");
    }

    @Test
    void deveGerarEValidarTokenComPapel() {
        String token = jwt.generateToken(42L, "admin@example.com", true, "ADMIN");

        AuthenticatedUser user = jwt.validateToken(token);

        assertThat(user.id()).isEqualTo(42L);
        assertThat(user.email()).isEqualTo("admin@example.com");
        assertThat(user.verificado()).isTrue();
        assertThat(user.papel()).isEqualTo("ADMIN");
    }

    @Test
    void deveRejeitarTokenAdulterado() {
        String token = jwt.generateToken(1L, "x@x.com", false);
        String adulterado = token.substring(0, token.length() - 2) + "ab";

        assertThatThrownBy(() -> jwt.validateToken(adulterado))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void deveRejeitarTokenComSecretDiferente() {
        String token = jwt.generateToken(1L, "x@x.com", false);
        JwtUtil outro = new JwtUtil("outro-segredo-tambem-com-mais-de-32-chars-aqui", EXP_MS);

        assertThatThrownBy(() -> outro.validateToken(token))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void deveExigirSecretMinimo32Bytes() {
        assertThatThrownBy(() -> new JwtUtil("curto", EXP_MS))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
