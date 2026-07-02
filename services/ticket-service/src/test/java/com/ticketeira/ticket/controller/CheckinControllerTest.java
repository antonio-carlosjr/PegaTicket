package com.ticketeira.ticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.client.EventResumo;
import com.ticketeira.ticket.domain.Ingresso;
import com.ticketeira.ticket.domain.Inscricao;
import com.ticketeira.ticket.domain.StatusIngresso;
import com.ticketeira.ticket.messaging.InscricaoCanceladaPublisher;
import com.ticketeira.ticket.repository.CheckinRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A2 — POST /tickets/checkin — controller + auth + ownership (MockMvc / H2).
 * Casos: A2.a A2.b A2.c A2.d A2.e A2.f A2.g A2.h [US-034]
 *
 * Gabarito: EncerrarEventoControllerTest.
 * EventClient @MockBean para controlar promotorId.
 *
 * VERMELHO: CheckinController, CheckinService, Checkin entity nao existem.
 */
@Tag("controller")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("A2 — POST /tickets/checkin auth + ownership")
class CheckinControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    InscricaoRepository inscricaoRepository;

    @Autowired
    IngressoRepository ingressoRepository;

    @Autowired
    CheckinRepository checkinRepository;

    /** EventClient mockado — H2 nao tem broker nem event-service real. */
    @MockBean
    EventClient eventClient;

    /** Publisher mockado — no perfil "test" nao ha Rabbit. */
    @MockBean
    InscricaoCanceladaPublisher inscricaoCanceladaPublisher;

    private static final Long PROMOTOR_DONO = 1L;
    private static final Long PROMOTOR_OUTRO = 2L;
    private static final Long PARTICIPANTE_ID = 99L;
    private static final Long EVENTO_ID = 10L;

    @BeforeEach
    void limpar() {
        checkinRepository.deleteAll();
        ingressoRepository.deleteAll();
        inscricaoRepository.deleteAll();
    }

    /**
     * Seed: cria inscricao ATIVA + ingresso ATIVO; retorna o codigoUnico do ingresso.
     * Mocka o EventClient para retornar o promotorId desejado.
     */
    private String seedInscricaoComIngresso(Long eventoId, Long promotorId) {
        when(eventClient.getEvento(anyLong()))
                .thenReturn(new EventResumo(
                        eventoId, "Show Teste", "GRATUITO", "PUBLICADO",
                        10, 100, null, promotorId,
                        OffsetDateTime.of(2026, 9, 1, 20, 0, 0, 0, ZoneOffset.of("-03:00")),
                        null, null
                ));

        Inscricao ins = Inscricao.criar(PARTICIPANTE_ID, eventoId);
        ins = inscricaoRepository.save(ins);
        Ingresso ing = Ingresso.emitir(ins.getId());
        ing = ingressoRepository.save(ing);
        return ing.getCodigoUnico();
    }

    private String body(String codigoUnico) throws Exception {
        return mapper.writeValueAsString(Map.of("codigoUnico", codigoUnico));
    }

    // A2.a -------------------------------------------------------------------

    /**
     * A2.a — PROMOTOR dono + ingresso ATIVO -> 200, status=UTILIZADO, cria checkins count=1. [US-034.1]
     */
    @Test
    @DisplayName("A2.a — promotor dono + ATIVO -> 200 + UTILIZADO + checkin criado")
    void checkin_promotorDono_ingressoAtivo_retorna200() throws Exception {
        String codigo = seedInscricaoComIngresso(EVENTO_ID, PROMOTOR_DONO);

        mvc.perform(post("/tickets/checkin")
                        .header("X-User-Id", PROMOTOR_DONO)
                        .header("X-User-Papel", "PROMOTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(codigo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UTILIZADO"))
                .andExpect(jsonPath("$.realizadoEm").isNotEmpty());

        assertThat(checkinRepository.count()).isEqualTo(1L);
    }

    // A2.b -------------------------------------------------------------------

    /**
     * A2.b — 2a leitura do mesmo QR -> 409 INGRESSO_JA_UTILIZADO; checkins count permanece 1. [US-034.2]
     */
    @Test
    @DisplayName("A2.b — 2o check-in do mesmo QR -> 409 INGRESSO_JA_UTILIZADO")
    void checkin_segundo_mesmoCodigo_retorna409() throws Exception {
        String codigo = seedInscricaoComIngresso(EVENTO_ID, PROMOTOR_DONO);

        // 1o check-in
        mvc.perform(post("/tickets/checkin")
                        .header("X-User-Id", PROMOTOR_DONO)
                        .header("X-User-Papel", "PROMOTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(codigo)))
                .andExpect(status().isOk());

        // 2o check-in
        mvc.perform(post("/tickets/checkin")
                        .header("X-User-Id", PROMOTOR_DONO)
                        .header("X-User-Papel", "PROMOTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(codigo)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("INGRESSO_JA_UTILIZADO"));

        assertThat(checkinRepository.count()).isEqualTo(1L);
    }

    // A2.c -------------------------------------------------------------------

    /**
     * A2.c — PROMOTOR nao-dono -> 403 CHECKIN_EVENTO_ALHEIO; ingresso permanece ATIVO. [US-034.3]
     */
    @Test
    @DisplayName("A2.c — promotor nao-dono -> 403 CHECKIN_EVENTO_ALHEIO")
    void checkin_promotorNaoDono_retorna403() throws Exception {
        // Seed com PROMOTOR_DONO como dono
        String codigo = seedInscricaoComIngresso(EVENTO_ID, PROMOTOR_DONO);

        mvc.perform(post("/tickets/checkin")
                        .header("X-User-Id", PROMOTOR_OUTRO)     // diferente do dono
                        .header("X-User-Papel", "PROMOTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(codigo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("CHECKIN_EVENTO_ALHEIO"));

        // Ingresso deve permanecer ATIVO
        var ing = ingressoRepository.findByCodigoUnico(codigo).orElseThrow();
        assertThat(ing.getStatus()).isEqualTo(StatusIngresso.ATIVO);
    }

    // A2.d -------------------------------------------------------------------

    /**
     * A2.d — codigoUnico inexistente -> 404 INGRESSO_NAO_ENCONTRADO. [US-034.4]
     */
    @Test
    @DisplayName("A2.d — codigoUnico inexistente -> 404 INGRESSO_NAO_ENCONTRADO")
    void checkin_codigoInexistente_retorna404() throws Exception {
        mvc.perform(post("/tickets/checkin")
                        .header("X-User-Id", PROMOTOR_DONO)
                        .header("X-User-Papel", "PROMOTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("codigo-que-nao-existe-00000000-0000")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("INGRESSO_NAO_ENCONTRADO"));
    }

    // A2.e -------------------------------------------------------------------

    /**
     * A2.e — ingresso CANCELADO -> 404 INGRESSO_NAO_ENCONTRADO. [US-034.4]
     */
    @Test
    @DisplayName("A2.e — ingresso CANCELADO -> 404 INGRESSO_NAO_ENCONTRADO")
    void checkin_ingressoCancelado_retorna404() throws Exception {
        when(eventClient.getEvento(anyLong()))
                .thenReturn(new EventResumo(
                        EVENTO_ID, "Show", "GRATUITO", "PUBLICADO",
                        10, 100, null, PROMOTOR_DONO,
                        OffsetDateTime.now().plusDays(30), null, null
                ));

        Inscricao ins = Inscricao.criar(PARTICIPANTE_ID, EVENTO_ID);
        ins = inscricaoRepository.save(ins);
        Ingresso ing = Ingresso.emitir(ins.getId());
        ing.cancelar();   // cancela o ingresso
        ing = ingressoRepository.save(ing);

        mvc.perform(post("/tickets/checkin")
                        .header("X-User-Id", PROMOTOR_DONO)
                        .header("X-User-Papel", "PROMOTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ing.getCodigoUnico())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("INGRESSO_NAO_ENCONTRADO"));
    }

    // A2.f -------------------------------------------------------------------

    /**
     * A2.f — papel PARTICIPANTE -> 403 "Acesso restrito a promotores." [US-034.5]
     */
    @Test
    @DisplayName("A2.f — papel PARTICIPANTE -> 403")
    void checkin_papelParticipante_retorna403() throws Exception {
        mvc.perform(post("/tickets/checkin")
                        .header("X-User-Id", PARTICIPANTE_ID)
                        .header("X-User-Papel", "PARTICIPANTE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("qualquer-codigo")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Acesso restrito a promotores."));
    }

    // A2.g -------------------------------------------------------------------

    /**
     * A2.g — sem X-User-Id -> 401. [US-034 / auth]
     */
    @Test
    @DisplayName("A2.g — sem X-User-Id -> 401")
    void checkin_semUserId_retorna401() throws Exception {
        mvc.perform(post("/tickets/checkin")
                        .header("X-User-Papel", "PROMOTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("qualquer-codigo")))
                .andExpect(status().isUnauthorized());
    }

    // A2.h -------------------------------------------------------------------

    /**
     * A2.h — body sem codigoUnico (blank) -> 400. [borda]
     */
    @Test
    @DisplayName("A2.h — body com codigoUnico blank -> 400 Bean Validation")
    void checkin_semCodigoUnico_retorna400() throws Exception {
        mvc.perform(post("/tickets/checkin")
                        .header("X-User-Id", PROMOTOR_DONO)
                        .header("X-User-Papel", "PROMOTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoUnico\": \"\"}"))
                .andExpect(status().isBadRequest());
    }
}
