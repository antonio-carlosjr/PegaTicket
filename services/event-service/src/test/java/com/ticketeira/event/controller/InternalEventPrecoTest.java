package com.ticketeira.event.controller;

import com.ticketeira.event.domain.Evento;
import com.ticketeira.event.domain.TipoEvento;
import com.ticketeira.event.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A7 — Extensao dos testes de auth do endpoint interno para incluir os campos preco e promotorId.
 * Os casos A7.a e A7.b (sem token -> 403; via gateway -> 404) ja estao cobertos em
 * InternalEventControllerTest.java da S3.
 * Esta classe adiciona A7.c: com token valido, evento PAGO inclui preco e promotorId.
 *
 * Nota: O evento PAGO usa o factory Evento.criar() com tipo=PAGO e preco nao nulo.
 * O Backend deve incluir o campo preco em EventoInternoResponse.from() (delta S4).
 * Casos de teste: A7 (tests-spec.md) — extensao para preco
 */
@Tag("integracao")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("A7 — GET /internal/events/{id}: preco e promotorId em evento PAGO")
class InternalEventPrecoTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    EventRepository eventRepository;

    private static final String TOKEN_OK = "test-internal-secret";

    @BeforeEach
    void limpar() {
        eventRepository.deleteAll();
    }

    @Test
    @DisplayName("A7.c — internalEvents_comToken_incluiPrecoEPromotor (evento PAGO)")
    void internalEvents_comToken_incluiPrecoEPromotor() throws Exception {
        // Criar evento PAGO com preco=150.00 e promotorId=5
        Evento eventoPago = Evento.criar(
                5L,                   // promotorId
                "Show Pago",
                null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Arena",
                TipoEvento.PAGO,
                100,
                new BigDecimal("150.00"),  // preco — NOVO campo S4
                null,
                null);
        eventoPago.publicar();
        Long id = eventRepository.save(eventoPago).getId();

        mvc.perform(get("/internal/events/{id}", id)
                        .header("X-Internal-Token", TOKEN_OK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.tipo").value("PAGO"))
                .andExpect(jsonPath("$.preco").value(150.00))
                .andExpect(jsonPath("$.promotorId").value(5))
                .andExpect(jsonPath("$.status").value("PUBLICADO"));
    }

    @Test
    @DisplayName("A7.d — internalEvents_semToken_retorna403 (regressao S3 — A7.a)")
    void internalEvents_semToken_retorna403() throws Exception {
        Evento e = Evento.criar(1L, "Show", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local", TipoEvento.GRATUITO, 10, null, null, null);
        e.publicar();
        Long id = eventRepository.save(e).getId();

        mvc.perform(get("/internal/events/{id}", id))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("ACESSO_INTERNO_NEGADO"));
    }

    @Test
    @DisplayName("A7.e — internalEvents_eventoGratuito_precoNull")
    void internalEvents_eventoGratuito_precoNull() throws Exception {
        // Evento GRATUITO: preco deve ser null no response
        Evento eventoGratuito = Evento.criar(1L, "Workshop", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Online", TipoEvento.GRATUITO, 50, null, null, null);
        eventoGratuito.publicar();
        Long id = eventRepository.save(eventoGratuito).getId();

        mvc.perform(get("/internal/events/{id}", id)
                        .header("X-Internal-Token", TOKEN_OK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("GRATUITO"))
                .andExpect(jsonPath("$.preco").doesNotExist());
    }
}
