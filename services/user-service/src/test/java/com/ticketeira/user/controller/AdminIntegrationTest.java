package com.ticketeira.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketeira.user.domain.Papel;
import com.ticketeira.user.dto.LoginRequest;
import com.ticketeira.user.dto.RejeicaoRequest;
import com.ticketeira.user.dto.RegisterRequest;
import com.ticketeira.user.domain.Usuario;
import com.ticketeira.user.domain.PerfilVerificado;
import com.ticketeira.user.domain.StatusVerificacao;
import com.ticketeira.user.repository.UsuarioRepository;
import com.ticketeira.user.repository.PerfilVerificadoRepository;
import com.ticketeira.user.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PerfilVerificadoRepository perfis;

    @Autowired
    private ObjectMapper objectMapper;

    private Long participanteId;
    private Long promotorId;

    @BeforeEach
    void setup() {
        // Criar um participante
        var p = authService.register(new RegisterRequest("Parti", "parti@x.com", "senha123", Papel.PARTICIPANTE, null, null, null, null, null, null, null, null, null, null, null, null));
        participanteId = p.id();

        // Criar um promotor
        var pr = authService.register(new RegisterRequest("Promo", "promo@x.com", "senha123", Papel.PROMOTOR, "111.111.111-11", "(11) 99999-9999", null, null, null, null, null, null, null, null, null, null));
        promotorId = pr.id();
    }

    @Test
    void participanteNaoPodeAcessarAdminEndpoints() throws Exception {
        mockMvc.perform(get("/users")
                .header("X-User-Papel", "PARTICIPANTE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void inativarUsuarioImpedeLogin() throws Exception {
        // Inativar participante
        mockMvc.perform(put("/users/" + participanteId + "/inativar")
                .header("X-User-Papel", "ADMIN"))
                .andExpect(status().isOk());

        // Tentar login
        LoginRequest req = new LoginRequest("parti@x.com", "senha123");
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized()); // Depende de como a exception de Conta Inativa é mapeada, mas BusinessException joga o status dela (ex: 401 ou 403)
    }

    @Test
    void aprovarPromotorMudaStatusEPapel() throws Exception {
        mockMvc.perform(put("/users/" + promotorId + "/aprovar")
                .header("X-User-Papel", "ADMIN"))
                .andExpect(status().isOk());

        Usuario u = usuarios.findById(promotorId).orElseThrow();
        assertThat(u.getPapel()).isEqualTo(Papel.PROMOTOR);
        assertThat(u.isVerificado()).isTrue();

        PerfilVerificado p = perfis.findByUsuarioId(promotorId).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(StatusVerificacao.VERIFICADO);
    }

    @Test
    void rejeitarPromotorGravaMotivo() throws Exception {
        RejeicaoRequest req = new RejeicaoRequest("Falta de documentos");
        mockMvc.perform(put("/users/" + promotorId + "/rejeitar")
                .header("X-User-Papel", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        PerfilVerificado p = perfis.findByUsuarioId(promotorId).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(StatusVerificacao.REJEITADO);
        assertThat(p.getMotivoRejeicao()).isEqualTo("Falta de documentos");
    }
}
