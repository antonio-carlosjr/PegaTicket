package com.ticketeira.user.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    @Test
    void tokensAleatoriosNaoColidem() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(TokenHasher.generateRawToken());
        }
        assertThat(tokens).hasSize(1000);
    }

    @Test
    void tokenTemTamanhoEsperado() {
        String token = TokenHasher.generateRawToken();
        // 32 bytes em base64url sem padding = 43 chars
        assertThat(token).hasSize(43);
        assertThat(token).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    void mesmoInputProduzMesmoHash() {
        String hash1 = TokenHasher.sha256Hex("teste");
        String hash2 = TokenHasher.sha256Hex("teste");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void inputDiferenteProduzHashDiferente() {
        String hashA = TokenHasher.sha256Hex("abc");
        String hashB = TokenHasher.sha256Hex("abd");
        assertThat(hashA).isNotEqualTo(hashB);
    }

    @Test
    void hashTemTamanho64HexChars() {
        String hash = TokenHasher.sha256Hex("qualquer-coisa");
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("^[0-9a-f]{64}$");
    }
}
