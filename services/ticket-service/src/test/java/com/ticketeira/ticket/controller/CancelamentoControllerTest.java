package com.ticketeira.ticket.controller;

import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.client.EventResumo;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B2 — DELETE /tickets/inscricoes/{id} — controller (MockMvc / H2). [US-035]
 * Casos: B2.a B2.b B2.c B2.d B2.e B2.f
 *
 * EventClient e InscricaoCanceladaPublisher @MockBean.
 *
 * VERMELHO: CancelamentoController/CancelamentoInscricaoService nao existem.
 */
@Tag("controller")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("B2 — DELETE /tickets/inscricoes/{id} controller")
class CancelamentoControllerTest {

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

    private static final Long USUARIO_ID = 10L;
    private static final Long OUTRO_USUARIO = 20L;
    private static final Long EVENTO_ID = 42L;

    @BeforeEach
    void limpar() {
        ingressoRepository.deleteAll();
        inscricaoRepository.deleteAll();
    }

    private Inscricao seedInscricaoAtiva(Long usuarioId) {
        Inscricao ins = Inscricao.criar(usuarioId, EVENTO_ID);
        return inscricaoRepository.save(ins);
    }

    private void mockEventoGratuito() {
        when(eventClient.getEvento(anyLong()))
                .thenReturn(new EventResumo(
                        EVENTO_ID, "Workshop", "GRATUITO", "PUBLICADO",
                        10, 100, null, 5L,
                        OffsetDateTime.now().plusDays(30), null, null
                ));
    }

    private void mockEventoPagoDentroDoPrazo() {
        when(eventClient.getEvento(anyLong()))
                .thenReturn(new EventResumo(
                        EVENTO_ID, "Show", "PAGO", "PUBLICADO",
                        10, 100, new BigDecimal("100.00"), 5L,
                        OffsetDateTime.now().plusDays(30), null, 7
                ));
    }

    private void mockEventoPagoForaDoPrazo() {
        when(eventClient.getEvento(anyLong()))
                .thenReturn(new EventResumo(
                        EVENTO_ID, "Show Passado", "PAGO", "PUBLICADO",
                        10, 100, new BigDecimal("100.00"), 5L,
                        OffsetDateTime.now().minusDays(1), null, 7
                ));
    }

    // B2.a -------------------------------------------------------------------

    /**
     * B2.a — DELETE inscricao gratuita propria -> 200, status=CANCELADA, reembolsoIniciado=false. [US-035.1]
     */
    @Test
    @DisplayName("B2.a — GRATUITO propria -> 200 + CANCELADA + reembolsoIniciado=false")
    void cancelar_gratuito_proprio_retorna200() throws Exception {
        mockEventoGratuito();
        Inscricao ins = seedInscricaoAtiva(USUARIO_ID);

        mvc.perform(delete("/tickets/inscricoes/{id}", ins.getId())
                        .header("X-User-Id", USUARIO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inscricaoId").value(ins.getId()))
                .andExpect(jsonPath("$.status").value("CANCELADA"))
                .andExpect(jsonPath("$.reembolsoIniciado").value(false));
    }

    // B2.b -------------------------------------------------------------------

    /**
     * B2.b — DELETE inscricao paga dentro do prazo -> 200, reembolsoIniciado=true. [US-035.2]
     */
    @Test
    @DisplayName("B2.b — PAGO dentro do prazo -> 200 + reembolsoIniciado=true")
    void cancelar_pago_dentroPrazo_retorna200() throws Exception {
        mockEventoPagoDentroDoPrazo();
        Inscricao ins = seedInscricaoAtiva(USUARIO_ID);

        mvc.perform(delete("/tickets/inscricoes/{id}", ins.getId())
                        .header("X-User-Id", USUARIO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"))
                .andExpect(jsonPath("$.reembolsoIniciado").value(true));
    }

    // B2.c -------------------------------------------------------------------

    /**
     * B2.c — DELETE inscricao paga fora do prazo -> 422 PRAZO_CANCELAMENTO_ENCERRADO. [US-035.3]
     */
    @Test
    @DisplayName("B2.c — PAGO fora do prazo -> 422 PRAZO_CANCELAMENTO_ENCERRADO")
    void cancelar_pago_foraPrazo_retorna422() throws Exception {
        mockEventoPagoForaDoPrazo();
        Inscricao ins = seedInscricaoAtiva(USUARIO_ID);

        mvc.perform(delete("/tickets/inscricoes/{id}", ins.getId())
                        .header("X-User-Id", USUARIO_ID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("PRAZO_CANCELAMENTO_ENCERRADO"));
    }

    // B2.d -------------------------------------------------------------------

    /**
     * B2.d — DELETE inscricao de outro usuario -> 403. [US-035.4]
     */
    @Test
    @DisplayName("B2.d — inscricao de outro -> 403 CANCELAMENTO_DE_OUTRO")
    void cancelar_inscricaoDeOutro_retorna403() throws Exception {
        mockEventoGratuito();
        Inscricao ins = seedInscricaoAtiva(OUTRO_USUARIO);   // dono = OUTRO_USUARIO

        mvc.perform(delete("/tickets/inscricoes/{id}", ins.getId())
                        .header("X-User-Id", USUARIO_ID))   // tenta cancelar como USUARIO_ID
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("CANCELAMENTO_DE_OUTRO"));
    }

    // B2.e -------------------------------------------------------------------

    /**
     * B2.e — sem X-User-Id -> 401. [auth]
     */
    @Test
    @DisplayName("B2.e — sem X-User-Id -> 401")
    void cancelar_semUserId_retorna401() throws Exception {
        mvc.perform(delete("/tickets/inscricoes/1"))
                .andExpect(status().isUnauthorized());
    }

    // B2.f -------------------------------------------------------------------

    /**
     * B2.f — {id} nao-numerico -> 400. [borda]
     */
    @Test
    @DisplayName("B2.f — id nao-numerico -> 400")
    void cancelar_idNaoNumerico_retorna400() throws Exception {
        mvc.perform(delete("/tickets/inscricoes/abc")
                        .header("X-User-Id", USUARIO_ID))
                .andExpect(status().isBadRequest());
    }
}
