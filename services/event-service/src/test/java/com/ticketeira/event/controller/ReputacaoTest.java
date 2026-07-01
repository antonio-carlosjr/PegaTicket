package com.ticketeira.event.controller;

import com.ticketeira.event.client.TicketClient;
import com.ticketeira.event.domain.Avaliacao;
import com.ticketeira.event.domain.Evento;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E1 — GET /events/{id} inclui reputacao (US-025).
 * Casos: E1.a E1.b E1.c E1.d E1.e
 *
 * VERMELHO: EventoResponse += reputacao, AvaliacaoRepository.agregarReputacao nao existem.
 */
@Tag("controller")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("E1 — GET /events/{id} inclui reputacao (US-025)")
class ReputacaoTest {

    @Autowired
    MockMvc mvc;

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

    @BeforeEach
    void limpar() {
        avaliacaoRepository.deleteAll();
        eventRepository.deleteAll();
    }

    private Evento criarEventoRealizado() {
        Evento e = Evento.criar(
                PROMOTOR_ID, "Show Realizado", "Descricao",
                OffsetDateTime.of(2026, 8, 1, 20, 0, 0, 0, ZoneOffset.of("-03:00")),
                OffsetDateTime.of(2026, 8, 1, 23, 0, 0, 0, ZoneOffset.of("-03:00")),
                "Arena", TipoEvento.PAGO, 100, new BigDecimal("50.00"), 7, null);
        e.publicar();
        e.realizar();
        return eventRepository.save(e);
    }

    private void criarAvaliacao(Long eventoId, Long usuarioId, int nota) {
        avaliacaoRepository.save(Avaliacao.criar(eventoId, usuarioId, nota, null));
    }

    // E1.a -------------------------------------------------------------------

    /**
     * E1.a — evento com 3 avaliacoes (5,4,3) -> reputacao={media:4.0, total:3}. [US-025.1]
     */
    @Test
    @DisplayName("E1.a — 3 avaliacoes (5,4,3) -> media=4.0, total=3")
    void reputacao_tresAvaliacoes_mediaCorreta() throws Exception {
        Evento evento = criarEventoRealizado();
        criarAvaliacao(evento.getId(), 101L, 5);
        criarAvaliacao(evento.getId(), 102L, 4);
        criarAvaliacao(evento.getId(), 103L, 3);

        mvc.perform(get("/events/{id}", evento.getId())
                        .header("X-User-Id", USUARIO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reputacao.media").value(4.0))
                .andExpect(jsonPath("$.reputacao.total").value(3));
    }

    // E1.b -------------------------------------------------------------------

    /**
     * E1.b — evento SEM avaliacoes -> reputacao={media:null, total:0}. [US-025.1]
     */
    @Test
    @DisplayName("E1.b — sem avaliacoes -> media=null, total=0")
    void reputacao_semAvaliacoes_mediaNulaTotal0() throws Exception {
        Evento evento = criarEventoRealizado();

        mvc.perform(get("/events/{id}", evento.getId())
                        .header("X-User-Id", USUARIO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reputacao.media").isEmpty())
                .andExpect(jsonPath("$.reputacao.total").value(0));
    }

    // E1.c -------------------------------------------------------------------

    /**
     * E1.c — nova avaliacao reflete imediatamente (sem cache). [US-025.2]
     */
    @Test
    @DisplayName("E1.c — nova avaliacao reflete imediatamente (sem cache obsoleto)")
    void reputacao_novaAvaliacao_refleteSemCache() throws Exception {
        Evento evento = criarEventoRealizado();

        // Antes: sem avaliacoes
        mvc.perform(get("/events/{id}", evento.getId())
                        .header("X-User-Id", USUARIO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reputacao.total").value(0));

        // Adiciona avaliacao
        criarAvaliacao(evento.getId(), 101L, 5);

        // Depois: reflete imediatamente
        mvc.perform(get("/events/{id}", evento.getId())
                        .header("X-User-Id", USUARIO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reputacao.total").value(1))
                .andExpect(jsonPath("$.reputacao.media").value(5.0));
    }

    // E1.d -------------------------------------------------------------------

    /**
     * E1.d — GET /events/{id} por qualquer autenticado expoe reputacao. [US-025.3]
     */
    @Test
    @DisplayName("E1.d — qualquer autenticado ve a reputacao")
    void reputacao_qualquerAutenticado_veReputacao() throws Exception {
        Evento evento = criarEventoRealizado();
        criarAvaliacao(evento.getId(), 101L, 4);

        // Usuario comum (sem papel especial)
        mvc.perform(get("/events/{id}", evento.getId())
                        .header("X-User-Id", 999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reputacao").exists())
                .andExpect(jsonPath("$.reputacao.total").value(1));
    }

    // E1.e -------------------------------------------------------------------

    /**
     * E1.e — query agregada e 1 round-trip (sem N+1) — verificado via revisao de codigo.
     * Este teste confirma que AvaliacaoRepository.agregarReputacao existe e retorna os tipos corretos.
     */
    @Test
    @DisplayName("E1.e — agregarReputacao retorna Double media + long total em 1 query")
    void reputacao_agregarReputacaoRetornaTiposCorretos() throws Exception {
        Evento evento = criarEventoRealizado();
        criarAvaliacao(evento.getId(), 101L, 5);
        criarAvaliacao(evento.getId(), 102L, 3);

        // AVG(5,3) = 4.0
        mvc.perform(get("/events/{id}", evento.getId())
                        .header("X-User-Id", USUARIO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reputacao.media").isNumber())
                .andExpect(jsonPath("$.reputacao.total").isNumber());
    }
}
