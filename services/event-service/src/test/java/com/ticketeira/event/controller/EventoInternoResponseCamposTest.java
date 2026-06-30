package com.ticketeira.event.controller;

import com.ticketeira.event.domain.Evento;
import com.ticketeira.event.domain.TipoEvento;
import com.ticketeira.event.messaging.EventoPublisher;
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
 * F1 / 6.1 — EventoInternoResponse expoe dataInicio + prazoReembolsoDias. [US-035 / ADR-T15]
 *
 * Verifica que GET /internal/events/{id} retorna os 2 novos campos.
 * Regressao: campos existentes ainda funcionam; fixtures 5A podem ser atualizadas.
 *
 * VERMELHO: EventoInternoResponse ainda nao tem dataInicio/prazoReembolsoDias.
 */
@Tag("controller")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("F1 — EventoInternoResponse expoe dataInicio + prazoReembolsoDias (ADR-T15)")
class EventoInternoResponseCamposTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    EventRepository eventRepository;

    @MockBean
    EventoPublisher eventoPublisher;

    private static final String TOKEN_OK = "test-internal-secret";
    private static final Long PROMOTOR_ID = 5L;

    @BeforeEach
    void limpar() {
        eventRepository.deleteAll();
    }

    private Long criarEventoPagoPublicado() {
        Evento e = Evento.criar(
                PROMOTOR_ID, "Show Pago", "Descricao",
                OffsetDateTime.of(2026, 9, 1, 20, 0, 0, 0, ZoneOffset.of("-03:00")),
                OffsetDateTime.of(2026, 9, 1, 23, 0, 0, 0, ZoneOffset.of("-03:00")),
                "Arena", TipoEvento.PAGO, 200, new BigDecimal("100.00"), 7, null);
        e.publicar();
        return eventRepository.save(e).getId();
    }

    private Long criarEventoGratuitoPublicado() {
        Evento e = Evento.criar(
                PROMOTOR_ID, "Workshop Gratuito", null,
                OffsetDateTime.now().plusDays(10),
                OffsetDateTime.now().plusDays(10).plusHours(2),
                "Online", TipoEvento.GRATUITO, 50, null, null, null);
        e.publicar();
        return eventRepository.save(e).getId();
    }

    /**
     * Evento PAGO: GET /internal/events/{id} deve retornar dataInicio e prazoReembolsoDias=7.
     */
    @Test
    @DisplayName("EventoInternoResponse PAGO expoe dataInicio e prazoReembolsoDias=7")
    void internoPago_expoeDataInicioEPrazo() throws Exception {
        Long id = criarEventoPagoPublicado();

        mvc.perform(get("/internal/events/{id}", id)
                        .header("X-Internal-Token", TOKEN_OK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataInicio").isNotEmpty())
                .andExpect(jsonPath("$.prazoReembolsoDias").value(7));
    }

    /**
     * Evento GRATUITO: prazoReembolsoDias = null; dataInicio presente.
     */
    @Test
    @DisplayName("EventoInternoResponse GRATUITO: prazoReembolsoDias=null, dataInicio presente")
    void internoGratuito_prazoNuloDataInicioPresente() throws Exception {
        Long id = criarEventoGratuitoPublicado();

        mvc.perform(get("/internal/events/{id}", id)
                        .header("X-Internal-Token", TOKEN_OK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataInicio").isNotEmpty())
                .andExpect(jsonPath("$.prazoReembolsoDias").isEmpty());
    }
}
