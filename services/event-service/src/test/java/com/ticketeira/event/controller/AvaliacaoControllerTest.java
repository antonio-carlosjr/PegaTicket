package com.ticketeira.event.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketeira.event.client.TicketClient;
import com.ticketeira.event.domain.Evento;
import com.ticketeira.event.domain.StatusEvento;
import com.ticketeira.event.domain.TipoEvento;
import com.ticketeira.event.messaging.EventoPublisher;
import com.ticketeira.event.repository.AvaliacaoRepository;
import com.ticketeira.event.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D1 — POST /events/{id}/avaliacoes — controller (MockMvc / H2). [US-024]
 * Casos: D1.a D1.b D1.c D1.d D1.e D1.f D1.g D1.h
 *
 * Gabarito: EncerrarEventoControllerTest.
 * TicketClient @MockBean para controlar participou().
 *
 * VERMELHO: AvaliacaoController, AvaliacaoService, Avaliacao entity, TicketClient nao existem.
 */
@Tag("controller")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("D1 — POST /events/{id}/avaliacoes controller (US-024)")
class AvaliacaoControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    AvaliacaoRepository avaliacaoRepository;

    @MockBean
    EventoPublisher eventoPublisher;

    @MockBean
    TicketClient ticketClient;

    private static final Long USUARIO_ID = 10L;
    private static final Long PROMOTOR_ID = 5L;
    private static final Long ADMIN_ID = 1L;

    @BeforeEach
    void limpar() {
        avaliacaoRepository.deleteAll();
        eventRepository.deleteAll();
    }

    /** Cria evento com status REALIZADO. */
    private Long criarEventoRealizado() {
        Evento e = Evento.criar(
                PROMOTOR_ID, "Show Realizado", "Descricao",
                OffsetDateTime.of(2026, 8, 1, 20, 0, 0, 0, ZoneOffset.of("-03:00")),
                OffsetDateTime.of(2026, 8, 1, 23, 0, 0, 0, ZoneOffset.of("-03:00")),
                "Arena", TipoEvento.PAGO, 100, new BigDecimal("50.00"), 7, null);
        e.publicar();
        e.realizar();   // status = REALIZADO
        return eventRepository.save(e).getId();
    }

    /** Cria evento com status PUBLICADO (nao REALIZADO). */
    private Long criarEventoPublicado() {
        Evento e = Evento.criar(
                PROMOTOR_ID, "Show Futuro", null,
                OffsetDateTime.now().plusDays(30),
                OffsetDateTime.now().plusDays(30).plusHours(3),
                "Arena", TipoEvento.GRATUITO, 100, null, null, null);
        e.publicar();
        return eventRepository.save(e).getId();
    }

    private String bodyAvaliacao(int nota, String comentario) throws Exception {
        return mapper.writeValueAsString(Map.of("nota", nota, "comentario", comentario));
    }

    private String bodyAvaliacaoSoNota(int nota) throws Exception {
        return mapper.writeValueAsString(Map.of("nota", nota));
    }

    // D1.a -------------------------------------------------------------------

    /**
     * D1.a — evento REALIZADO + participou=true + nota 4 -> 201 AvaliacaoResponse; grava avaliacoes. [US-024.1]
     */
    @Test
    @DisplayName("D1.a — elegivel (REALIZADO + participou=true) -> 201 AvaliacaoResponse")
    void avaliar_elegivel_retorna201() throws Exception {
        Long eventoId = criarEventoRealizado();
        when(ticketClient.participou(USUARIO_ID, eventoId)).thenReturn(true);

        mvc.perform(post("/events/{id}/avaliacoes", eventoId)
                        .header("X-User-Id", USUARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAvaliacao(4, "Show incrivel!")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventoId").value(eventoId))
                .andExpect(jsonPath("$.nota").value(4))
                .andExpect(jsonPath("$.avaliadoEm").isNotEmpty());
    }

    // D1.b -------------------------------------------------------------------

    /**
     * D1.b — 2a avaliacao mesmo usuario+evento -> 409 AVALIACAO_DUPLICADA (UNIQUE). [US-024.2]
     */
    @Test
    @DisplayName("D1.b — 2a avaliacao -> 409 AVALIACAO_DUPLICADA")
    void avaliar_duplicada_retorna409() throws Exception {
        Long eventoId = criarEventoRealizado();
        when(ticketClient.participou(anyLong(), anyLong())).thenReturn(true);

        // 1a avaliacao
        mvc.perform(post("/events/{id}/avaliacoes", eventoId)
                        .header("X-User-Id", USUARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAvaliacaoSoNota(5)))
                .andExpect(status().isCreated());

        // 2a avaliacao
        mvc.perform(post("/events/{id}/avaliacoes", eventoId)
                        .header("X-User-Id", USUARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAvaliacaoSoNota(3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("AVALIACAO_DUPLICADA"));
    }

    // D1.c -------------------------------------------------------------------

    /**
     * D1.c — evento REALIZADO + participou=false -> 403 AVALIACAO_NAO_ELEGIVEL. [US-024.3]
     */
    @Test
    @DisplayName("D1.c — participou=false -> 403 AVALIACAO_NAO_ELEGIVEL")
    void avaliar_naoParticipou_retorna403() throws Exception {
        Long eventoId = criarEventoRealizado();
        when(ticketClient.participou(USUARIO_ID, eventoId)).thenReturn(false);

        mvc.perform(post("/events/{id}/avaliacoes", eventoId)
                        .header("X-User-Id", USUARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAvaliacaoSoNota(4)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("AVALIACAO_NAO_ELEGIVEL"));
    }

    // D1.d -------------------------------------------------------------------

    /**
     * D1.d — evento NAO REALIZADO (PUBLICADO) -> 403 AVALIACAO_NAO_ELEGIVEL (pre-filtro local). [US-024.3]
     */
    @Test
    @DisplayName("D1.d — evento PUBLICADO (nao REALIZADO) -> 403 AVALIACAO_NAO_ELEGIVEL")
    void avaliar_eventoNaoRealizado_retorna403() throws Exception {
        Long eventoId = criarEventoPublicado();

        mvc.perform(post("/events/{id}/avaliacoes", eventoId)
                        .header("X-User-Id", USUARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAvaliacaoSoNota(4)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("AVALIACAO_NAO_ELEGIVEL"));
    }

    // D1.e -------------------------------------------------------------------

    /**
     * D1.e — nota=0 -> 400 (Bean Validation @Min); nota=6 -> 400 (@Max). [US-024.4]
     */
    @Test
    @DisplayName("D1.e — nota=0 -> 400 Bean Validation")
    void avaliar_notaZero_retorna400() throws Exception {
        Long eventoId = criarEventoRealizado();

        mvc.perform(post("/events/{id}/avaliacoes", eventoId)
                        .header("X-User-Id", USUARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAvaliacaoSoNota(0)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("D1.e — nota=6 -> 400 Bean Validation")
    void avaliar_notaSeis_retorna400() throws Exception {
        Long eventoId = criarEventoRealizado();

        mvc.perform(post("/events/{id}/avaliacoes", eventoId)
                        .header("X-User-Id", USUARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAvaliacaoSoNota(6)))
                .andExpect(status().isBadRequest());
    }

    // D1.f -------------------------------------------------------------------

    /**
     * D1.f — admin/promotor nao-participante (participou=false) -> 403. [US-024.5]
     */
    @Test
    @DisplayName("D1.f — admin nao-participante -> 403 AVALIACAO_NAO_ELEGIVEL")
    void avaliar_adminNaoParticipante_retorna403() throws Exception {
        Long eventoId = criarEventoRealizado();
        when(ticketClient.participou(ADMIN_ID, eventoId)).thenReturn(false);

        mvc.perform(post("/events/{id}/avaliacoes", eventoId)
                        .header("X-User-Id", ADMIN_ID)
                        .header("X-User-Papel", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAvaliacaoSoNota(5)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("AVALIACAO_NAO_ELEGIVEL"));
    }

    // D1.g -------------------------------------------------------------------

    /**
     * D1.g — sem X-User-Id -> 401. [auth]
     */
    @Test
    @DisplayName("D1.g — sem X-User-Id -> 401")
    void avaliar_semUserId_retorna401() throws Exception {
        Long eventoId = criarEventoRealizado();

        mvc.perform(post("/events/{id}/avaliacoes", eventoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAvaliacaoSoNota(4)))
                .andExpect(status().isUnauthorized());
    }

    // D1.h -------------------------------------------------------------------

    /**
     * D1.h — evento inexistente -> 404 "Evento nao encontrado." [borda]
     */
    @Test
    @DisplayName("D1.h — evento inexistente -> 404")
    void avaliar_eventoInexistente_retorna404() throws Exception {
        mvc.perform(post("/events/{id}/avaliacoes", 99999L)
                        .header("X-User-Id", USUARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAvaliacaoSoNota(4)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Evento nao encontrado."));
    }
}
