package com.ticketeira.user;

import com.ticketeira.user.domain.Papel;
import com.ticketeira.user.dto.LoginRequest;
import com.ticketeira.user.dto.LoginResponse;
import com.ticketeira.user.dto.RegisterRequest;
import com.ticketeira.user.dto.UsuarioResponse;
import com.ticketeira.user.repository.PerfilVerificadoRepository;
import com.ticketeira.user.service.AuthService;
import com.ticketeira.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceApplicationTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private PerfilVerificadoRepository perfis;

    private static RegisterRequest participante(String nome, String email, String senha) {
        return new RegisterRequest(nome, email, senha, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static RegisterRequest promotor(String nome, String email, String senha, String cpf, String tel) {
        return new RegisterRequest(nome, email, senha, Papel.PROMOTOR, cpf, tel, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void contextLoads() {
        assertThat(authService).isNotNull();
        assertThat(userService).isNotNull();
    }

    @Test
    void registerComoParticipanteJaVerificado() {
        UsuarioResponse u = authService.register(participante("Ana", "ana@example.com", "senha123"));

        assertThat(u.id()).isNotNull();
        assertThat(u.papel()).isEqualTo(Papel.PARTICIPANTE);
        assertThat(u.verificado()).isTrue();
    }

    @Test
    void registerComoPromotorFicaPendente() {
        UsuarioResponse u = authService.register(promotor(
                "Carlos", "carlos@x.com", "senha123",
                "123.456.789-00", "(11) 91234-5678"));

        assertThat(u.papel()).isEqualTo(Papel.PARTICIPANTE);
        assertThat(u.verificado()).isFalse();

        assertThat(perfis.findByUsuarioId(u.id())).isPresent();
    }

    @Test
    void promotorSemCpfFalha() {
        assertThatThrownBy(() -> authService.register(promotor(
                "Sem CPF", "sc@x.com", "senha123", null, "(11) 91234-5678")))
                .hasMessageContaining("CPF e obrigatorio");
    }

    @Test
    void promotorComCpfDuplicadoFalha() {
        authService.register(promotor("A", "a@x.com", "senha123",
                "111.222.333-44", "(11) 91234-5678"));

        assertThatThrownBy(() -> authService.register(promotor(
                "B", "b@x.com", "senha123",
                "111.222.333-44", "(11) 91234-5679")))
                .hasMessageContaining("CPF ja cadastrado");
    }

    @Test
    void registroAdminEhBloqueado() {
        RegisterRequest req = new RegisterRequest(
                "Hacker", "h@x.com", "senha123", Papel.ADMIN, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> authService.register(req))
                .hasMessageContaining("ADMIN");
    }

    @Test
    void loginRetornaTokenEPapel() {
        authService.register(participante("Ana", "ana@example.com", "senha123"));

        LoginResponse r = authService.login(new LoginRequest("ana@example.com", "senha123"));

        assertThat(r.token()).isNotBlank();
        assertThat(r.papel()).isEqualTo(Papel.PARTICIPANTE);
        assertThat(r.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void naoPodeRegistrarEmailDuplicado() {
        authService.register(participante("Joao", "joao@x.com", "senha123"));
        assertThatThrownBy(() ->
                authService.register(participante("Outro", "joao@x.com", "senha123")))
                .hasMessageContaining("ja cadastrado");
    }

    @Test
    void loginComSenhaErradaFalha() {
        authService.register(participante("Maria", "maria@x.com", "senha123"));
        assertThatThrownBy(() ->
                authService.login(new LoginRequest("maria@x.com", "errada")))
                .hasMessageContaining("Credenciais");
    }
}
