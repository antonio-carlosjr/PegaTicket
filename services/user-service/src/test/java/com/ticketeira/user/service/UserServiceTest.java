package com.ticketeira.user.service;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.common.security.JwtUtil;
import com.ticketeira.user.domain.Usuario;
import com.ticketeira.user.dto.AtualizarPerfilRequest;
import com.ticketeira.user.dto.TrocarSenhaRequest;
import com.ticketeira.user.repository.PerfilVerificadoRepository;
import com.ticketeira.user.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UsuarioRepository usuarios;
    @Mock PerfilVerificadoRepository perfis;
    @Mock PasswordEncoder encoder;
    @Mock JwtUtil jwtUtil;
    @InjectMocks UserService service;

    private Usuario user;

    @BeforeEach
    void setUp() {
        user = Usuario.novoParticipante("Ana", "ana@x.com", "hashAtual");
        when(usuarios.findById(1L)).thenReturn(Optional.of(user));
    }

    @Test
    void trocarSenha_senhaAtualCorreta_atualizaHash() {
        when(encoder.matches("atual", "hashAtual")).thenReturn(true);
        when(encoder.encode("Nova@123")).thenReturn("hashNovo");

        service.trocarSenha(1L, new TrocarSenhaRequest("atual", "Nova@123"));

        assertThat(user.getSenhaHash()).isEqualTo("hashNovo");
        verify(usuarios).save(user);
    }

    @Test
    void trocarSenha_senhaAtualIncorreta_lanca400_eNaoSalva() {
        when(encoder.matches("errada", "hashAtual")).thenReturn(false);

        assertThatThrownBy(() -> service.trocarSenha(1L, new TrocarSenhaRequest("errada", "Nova@123")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Senha atual incorreta");
        verify(usuarios, never()).save(any());
    }

    @Test
    void atualizarPerfil_semPerfilRico_atualizaApenasNome() {
        when(perfis.findByUsuarioId(1L)).thenReturn(Optional.empty());
        AtualizarPerfilRequest req = new AtualizarPerfilRequest(
                "Ana Maria", null, null, null, null, null, null, null, null, null, null, null, null);

        service.atualizarPerfil(1L, req);

        assertThat(user.getNome()).isEqualTo("Ana Maria");
        verify(usuarios).save(user);
        verify(perfis, never()).save(any());
    }
}
