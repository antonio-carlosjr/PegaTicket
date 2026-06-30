package com.ticketeira.ticket.controller;

import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.domain.Ingresso;
import com.ticketeira.ticket.domain.Inscricao;
import com.ticketeira.ticket.messaging.InscricaoCanceladaPublisher;
import com.ticketeira.ticket.repository.IngressoRepository;
import com.ticketeira.ticket.repository.InscricaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D3 — GET /internal/tickets/participou — canal interno, auth + boolean correto. [US-024]
 * Casos: D3.a D3.b D3.c D3.d D3.e D3.f
 *
 * Gabarito: InternalEventControllerTest (event-service).
 * Token: "test-internal-secret" (application-test.yml: app.internal.token).
 *
 * VERMELHO: InternalTicketController nao existe.
 */
@Tag("controller")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("D3 — GET /internal/tickets/participou (US-024 / ADR-T08)")
class InternalTicketControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    InscricaoRepository inscricaoRepository;

    @Autowired
    IngressoRepository ingressoRepository;

    @MockBean
    EventClient eventClient;

    @MockBean
    InscricaoCanceladaPublisher inscricaoCanceladaPublisher;

    private static final String TOKEN_OK = "test-internal-secret";
    private static final String TOKEN_ERRADO = "token-errado";
    private static final Long USUARIO_ID = 10L;
    private static final Long EVENTO_ID = 42L;

    @BeforeEach
    void limpar() {
        ingressoRepository.deleteAll();
        inscricaoRepository.deleteAll();
    }

    private Inscricao seedInscricaoAtiva(Long usuarioId, Long eventoId) {
        return inscricaoRepository.save(Inscricao.criar(usuarioId, eventoId));
    }

    private Inscricao seedInscricaoCancelada(Long usuarioId, Long eventoId) {
        Inscricao ins = Inscricao.criar(usuarioId, eventoId);
        ins.cancelarPorEvento();
        return inscricaoRepository.save(ins);
    }

    private Ingresso seedIngressoUtilizado(Long inscricaoId) {
        Ingresso ing = Ingresso.emitir(inscricaoId);
        ing.utilizar();
        return ingressoRepository.save(ing);
    }

    // D3.a -------------------------------------------------------------------

    /**
     * D3.a — sem X-Internal-Token -> 403 ACESSO_INTERNO_NEGADO. [US-024 / ADR-T08]
     */
    @Test
    @DisplayName("D3.a — sem X-Internal-Token -> 403 ACESSO_INTERNO_NEGADO")
    void participou_semToken_retorna403() throws Exception {
        mvc.perform(get("/internal/tickets/participou")
                        .param("usuarioId", USUARIO_ID.toString())
                        .param("eventoId", EVENTO_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("ACESSO_INTERNO_NEGADO"));
    }

    // D3.b -------------------------------------------------------------------

    /**
     * D3.b — com token: usuario com Ingresso UTILIZADO no evento -> {participou:true}. [US-024 / PO-D1]
     */
    @Test
    @DisplayName("D3.b — ingresso UTILIZADO no evento -> participou=true")
    void participou_ingressoUtilizado_retornaTrue() throws Exception {
        Inscricao ins = seedInscricaoAtiva(USUARIO_ID, EVENTO_ID);
        seedIngressoUtilizado(ins.getId());

        mvc.perform(get("/internal/tickets/participou")
                        .header("X-Internal-Token", TOKEN_OK)
                        .param("usuarioId", USUARIO_ID.toString())
                        .param("eventoId", EVENTO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participou").value(true));
    }

    // D3.c -------------------------------------------------------------------

    /**
     * D3.c — com token: usuario com Inscricao ATIVA no evento -> {participou:true}. [US-024 / PO-D1]
     */
    @Test
    @DisplayName("D3.c — inscricao ATIVA no evento -> participou=true")
    void participou_inscricaoAtiva_retornaTrue() throws Exception {
        seedInscricaoAtiva(USUARIO_ID, EVENTO_ID);

        mvc.perform(get("/internal/tickets/participou")
                        .header("X-Internal-Token", TOKEN_OK)
                        .param("usuarioId", USUARIO_ID.toString())
                        .param("eventoId", EVENTO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participou").value(true));
    }

    // D3.d -------------------------------------------------------------------

    /**
     * D3.d — com token: usuario com Inscricao CANCELADA (e sem ingresso utilizado) -> {participou:false}. [US-024 / PO-D1]
     */
    @Test
    @DisplayName("D3.d — inscricao CANCELADA sem ingresso utilizado -> participou=false")
    void participou_inscricaoCancelada_retornaFalse() throws Exception {
        seedInscricaoCancelada(USUARIO_ID, EVENTO_ID);

        mvc.perform(get("/internal/tickets/participou")
                        .header("X-Internal-Token", TOKEN_OK)
                        .param("usuarioId", USUARIO_ID.toString())
                        .param("eventoId", EVENTO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participou").value(false));
    }

    // D3.e -------------------------------------------------------------------

    /**
     * D3.e — com token: usuario sem vinculo -> {participou:false}. [US-024]
     */
    @Test
    @DisplayName("D3.e — usuario sem vinculo com o evento -> participou=false")
    void participou_semVinculo_retornaFalse() throws Exception {
        mvc.perform(get("/internal/tickets/participou")
                        .header("X-Internal-Token", TOKEN_OK)
                        .param("usuarioId", USUARIO_ID.toString())
                        .param("eventoId", EVENTO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participou").value(false));
    }

    // D3.f -------------------------------------------------------------------

    /**
     * D3.f — usuarioId/eventoId ausente ou nao-numerico -> 400 (nunca 500). [borda]
     */
    @Test
    @DisplayName("D3.f — usuarioId ausente -> 400")
    void participou_semUsuarioId_retorna400() throws Exception {
        mvc.perform(get("/internal/tickets/participou")
                        .header("X-Internal-Token", TOKEN_OK)
                        .param("eventoId", EVENTO_ID.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("D3.f — eventoId nao-numerico -> 400 (nunca 500)")
    void participou_eventoIdNaoNumerico_retorna400() throws Exception {
        mvc.perform(get("/internal/tickets/participou")
                        .header("X-Internal-Token", TOKEN_OK)
                        .param("usuarioId", USUARIO_ID.toString())
                        .param("eventoId", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("D3.f — token errado -> 403")
    void participou_tokenErrado_retorna403() throws Exception {
        mvc.perform(get("/internal/tickets/participou")
                        .header("X-Internal-Token", TOKEN_ERRADO)
                        .param("usuarioId", USUARIO_ID.toString())
                        .param("eventoId", EVENTO_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("ACESSO_INTERNO_NEGADO"));
    }
}
