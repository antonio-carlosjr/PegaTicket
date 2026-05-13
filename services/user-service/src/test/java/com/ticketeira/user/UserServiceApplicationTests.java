package com.ticketeira.user;

import com.ticketeira.user.dto.LoginRequest;
import com.ticketeira.user.dto.LoginResponse;
import com.ticketeira.user.dto.RegisterRequest;
import com.ticketeira.user.dto.UsuarioResponse;
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

    @Test
    void contextLoads() {
        assertThat(authService).isNotNull();
        assertThat(userService).isNotNull();
    }

    @Test
    void registerELoginDevolvemTokenValido() {
        UsuarioResponse registered = authService.register(
                new RegisterRequest("Ana", "ana@example.com", "senha123"));
        assertThat(registered.id()).isNotNull();
        assertThat(registered.verificado()).isFalse();

        LoginResponse login = authService.login(new LoginRequest("ana@example.com", "senha123"));
        assertThat(login.token()).isNotBlank();
        assertThat(login.tokenType()).isEqualTo("Bearer");
        assertThat(login.userId()).isEqualTo(registered.id());
    }

    @Test
    void naoPodeRegistrarEmailDuplicado() {
        authService.register(new RegisterRequest("Joao", "joao@x.com", "senha123"));
        assertThatThrownBy(() ->
                authService.register(new RegisterRequest("Outro", "joao@x.com", "senha123")))
                .hasMessageContaining("ja cadastrado");
    }

    @Test
    void loginComSenhaErradaFalha() {
        authService.register(new RegisterRequest("Maria", "maria@x.com", "senha123"));
        assertThatThrownBy(() ->
                authService.login(new LoginRequest("maria@x.com", "errada")))
                .hasMessageContaining("Credenciais");
    }
}
