package com.ticketeira.event.controller;

import com.ticketeira.event.domain.StatusEvento;
import com.ticketeira.event.domain.TipoEvento;
import com.ticketeira.event.dto.EventoCreateRequest;
import com.ticketeira.event.messaging.EventoPublisher;
import com.ticketeira.event.repository.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A2 — POST /events/{id}/encerrar — auth + ownership (MockMvc / H2).
 * Casos: A2.a A2.b A2.c A2.d A2.e A2.f (tests-spec.md, US-043).
 * Perfil "test" (H2 + sem Rabbit): publica via MockBean/no-op.
 * VERMELHO: endpoint /events/{id}/encerrar ainda nao existe.
 */
@Tag("controller")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("A2 — POST /events/{id}/encerrar auth + ownership")
class EncerrarEventoControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    EventRepository eventRepository;

    /**
     * MockBean para o EventoPublisher: no perfil "test" (H2), o Rabbit e excluido.
     * O EventService dependera do EventoPublisher; aqui e mockado como no-op para que
     * o controller test nao precise do broker real.
     * VERMELHO: EventoPublisher nao existe ainda.
     */
    @MockBean
    EventoPublisher eventoPublisher;

    private static final Long PROMOTOR_A = 1L;
    private static final Long PROMOTOR_B = 2L;
    private static final Long PARTICIPANTE_ID = 99L;

    @BeforeEach
    void limpar() {
        eventRepository.deleteAll();
    }

    /** Cria e publica um evento do PROMOTOR_A; retorna o id. */
    private Long criarEventoPublicado(Long promotorId) throws Exception {
        EventoCreateRequest req = new EventoCreateRequest(
                "Show da Terra", "Descricao",
                OffsetDateTime.of(2026, 9, 1, 20, 0, 0, 0, ZoneOffset.of("-03:00")),
                OffsetDateTime.of(2026, 9, 1, 23, 0, 0, 0, ZoneOffset.of("-03:00")),
                "Arena Norte", TipoEvento.PAGO,
                200, new BigDecimal("50.00"), 7, null);

        MvcResult criarResult = mvc.perform(post("/events")
                        .header("X-User-Id", promotorId)
                        .header("X-User-Papel", "PROMOTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long eventoId = mapper.readTree(criarResult.getResponse().getContentAsString())
                .get("id").asLong();

        mvc.perform(post("/events/" + eventoId + "/publicar")
                        .header("X-User-Id", promotorId)
                        .header("X-User-Papel", "PROMOTOR"))
                .andExpect(status().isOk());

        return eventoId;
    }

    /** Cria um evento RASCUNHO (sem publicar). */
    private Long criarEventoRascunho(Long promotorId) throws Exception {
        EventoCreateRequest req = new EventoCreateRequest(
                "Rascunho Evento", null,
                OffsetDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.of("-03:00")),
                OffsetDateTime.of(2026, 10, 1, 14, 0, 0, 0, ZoneOffset.of("-03:00")),
                "Local", TipoEvento.GRATUITO,
                100, null, null, null);

        MvcResult result = mvc.perform(post("/events")
                        .header("X-User-Id", promotorId)
                        .header("X-User-Papel", "PROMOTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // ---- A2.a ---------------------------------------------------------------

    /**
     * A2.a — encerrar_promotorDono_retorna200_eStatusRealizado [US-043]
     * PROMOTOR + owner + PUBLICADO → 200, body status=REALIZADO.
     */
    @Test
    @DisplayName("A2.a — promotor dono + PUBLICADO → 200 + status=REALIZADO")
    void encerrar_promotorDono_retorna200_eStatusRealizado() throws Exception {
        Long eventoId = criarEventoPublicado(PROMOTOR_A);

        mvc.perform(post("/events/" + eventoId + "/encerrar")
                        .header("X-User-Id", PROMOTOR_A)
                        .header("X-User-Papel", "PROMOTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(StatusEvento.REALIZADO.name()))
                .andExpect(jsonPath("$.id").value(eventoId));
    }

    // ---- A2.b ---------------------------------------------------------------

    /**
     * A2.b — encerrar_semUserId_retorna401 [US-043]
     */
    @Test
    @DisplayName("A2.b — sem X-User-Id → 401")
    void encerrar_semUserId_retorna401() throws Exception {
        mvc.perform(post("/events/1/encerrar")
                        .header("X-User-Papel", "PROMOTOR"))
                .andExpect(status().isUnauthorized());
    }

    // ---- A2.c ---------------------------------------------------------------

    /**
     * A2.c — encerrar_papelParticipante_retorna403 [US-043]
     * X-User-Papel != PROMOTOR → 403 "Acesso restrito a promotores."
     */
    @Test
    @DisplayName("A2.c — papel PARTICIPANTE → 403")
    void encerrar_papelParticipante_retorna403() throws Exception {
        Long eventoId = criarEventoPublicado(PROMOTOR_A);

        mvc.perform(post("/events/" + eventoId + "/encerrar")
                        .header("X-User-Id", PARTICIPANTE_ID)
                        .header("X-User-Papel", "PARTICIPANTE"))
                .andExpect(status().isForbidden());
    }

    // ---- A2.d ---------------------------------------------------------------

    /**
     * A2.d — encerrar_naoDono_retorna404 [US-043]
     * Promotor diferente → 404 (nao vaza existencia — padrao carregarComOwnership).
     */
    @Test
    @DisplayName("A2.d — promotor nao-dono → 404 (nao vaza existencia)")
    void encerrar_naoDono_retorna404() throws Exception {
        Long eventoId = criarEventoPublicado(PROMOTOR_A);

        mvc.perform(post("/events/" + eventoId + "/encerrar")
                        .header("X-User-Id", PROMOTOR_B)
                        .header("X-User-Papel", "PROMOTOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Evento nao encontrado."));
    }

    // ---- A2.e ---------------------------------------------------------------

    /**
     * A2.e — encerrar_eventoRascunho_retorna409 [US-043]
     * Evento RASCUNHO → 409 TRANSICAO_INVALIDA.
     */
    @Test
    @DisplayName("A2.e — evento RASCUNHO → 409 TRANSICAO_INVALIDA")
    void encerrar_eventoRascunho_retorna409() throws Exception {
        Long eventoId = criarEventoRascunho(PROMOTOR_A);

        mvc.perform(post("/events/" + eventoId + "/encerrar")
                        .header("X-User-Id", PROMOTOR_A)
                        .header("X-User-Papel", "PROMOTOR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("TRANSICAO_INVALIDA"));
    }

    // ---- A2.f ---------------------------------------------------------------

    /**
     * A2.f — encerrar_idNaoNumerico_retorna400 [US-043]
     * {id} nao-numerico → 400, nunca 500.
     */
    @Test
    @DisplayName("A2.f — id nao-numerico → 400 (nunca 500)")
    void encerrar_idNaoNumerico_retorna400() throws Exception {
        mvc.perform(post("/events/abc/encerrar")
                        .header("X-User-Id", PROMOTOR_A)
                        .header("X-User-Papel", "PROMOTOR"))
                .andExpect(status().isBadRequest());
    }
}
