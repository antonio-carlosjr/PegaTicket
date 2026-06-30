package com.ticketeira.event.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketeira.event.TestcontainersBase;
import com.ticketeira.event.domain.TipoEvento;
import com.ticketeira.event.dto.EventoCreateRequest;
import com.ticketeira.event.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A3/A4 — Publicacao de evento.finalizado e evento.cancelado em afterCommit.
 * Testes de integracao com Postgres + RabbitMQ reais (Testcontainers).
 * Casos: A3.a, A4.a, A4.b (tests-spec.md, US-043/US-042).
 *
 * VERMELHO: EventoPublisher, records EventoFinalizadoEvent/EventoCanceladoEvent e o
 * endpoint /events/{id}/encerrar ainda nao existem.
 *
 * Nota: purga as filas no @BeforeEach (aprendizado S4 — isolamento entre testes
 * que compartilham o mesmo contexto/broker).
 */
@Tag("integracao")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test-postgres")
@DisplayName("A3/A4 — Publicacao AMQP apos encerrar/cancelar (afterCommit)")
class EventoPublicacaoIntegrationTest extends TestcontainersBase {

    private static final String EXCHANGE           = "ticketeira.events";
    private static final String QUEUE_FINALIZADO   = "evento.finalizado";
    private static final String QUEUE_CANCELADO    = "evento.cancelado";
    private static final String QUEUE_CANCELADO_TK = "evento.cancelado.ticket";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    RabbitAdmin rabbitAdmin;

    @BeforeEach
    void limpar() {
        eventRepository.deleteAll();
        // Aprendizado S4: purgar filas antes de cada teste para isolamento.
        // Classes que compartilham contexto/broker podem deixar mensagens residuais.
        rabbitAdmin.purgeQueue(QUEUE_FINALIZADO, false);
        rabbitAdmin.purgeQueue(QUEUE_CANCELADO, false);
        rabbitAdmin.purgeQueue(QUEUE_CANCELADO_TK, false);
    }

    /** Cria evento PUBLICADO do promotorId e retorna o id. */
    private Long criarEventoPublicado(Long promotorId) throws Exception {
        EventoCreateRequest req = new EventoCreateRequest(
                "Show da Terra", "Descricao",
                OffsetDateTime.of(2026, 9, 1, 20, 0, 0, 0, ZoneOffset.of("-03:00")),
                OffsetDateTime.of(2026, 9, 1, 23, 0, 0, 0, ZoneOffset.of("-03:00")),
                "Arena Norte", TipoEvento.PAGO,
                200, new BigDecimal("50.00"), 7, null);

        MvcResult result = mvc.perform(post("/events")
                        .header("X-User-Id", promotorId)
                        .header("X-User-Papel", "PROMOTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Long eventoId = mapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();

        mvc.perform(post("/events/" + eventoId + "/publicar")
                        .header("X-User-Id", promotorId)
                        .header("X-User-Papel", "PROMOTOR"))
                .andExpect(status().isOk());

        return eventoId;
    }

    // ---- A3.a ---------------------------------------------------------------

    /**
     * A3.a — encerrar_publicaEventoFinalizado_aposCommit [US-043]
     * Apos encerrar (PUBLICADO->REALIZADO), mensagem aparece na fila evento.finalizado
     * com eventId != null, eventoId correto, promotorId correto.
     * VERMELHO: endpoint /encerrar e EventoPublisher nao existem.
     */
    @Test
    @DisplayName("A3.a — encerrar → evento.finalizado com eventId/eventoId/promotorId")
    void encerrar_publicaEventoFinalizado_aposCommit() throws Exception {
        Long promotorId = 7L;
        Long eventoId = criarEventoPublicado(promotorId);

        mvc.perform(post("/events/" + eventoId + "/encerrar")
                        .header("X-User-Id", promotorId)
                        .header("X-User-Papel", "PROMOTOR"))
                .andExpect(status().isOk());

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Object msg = rabbitTemplate.receiveAndConvert(QUEUE_FINALIZADO, 500);
            assertThat(msg).isNotNull();
            // O payload deve ser um EventoFinalizadoEvent (ou Map se conversao for generica)
            assertThat(msg).isInstanceOf(EventoFinalizadoEvent.class);
            EventoFinalizadoEvent evento = (EventoFinalizadoEvent) msg;
            assertThat(evento.eventId()).isNotNull();
            assertThat(evento.eventoId()).isEqualTo(eventoId);
            assertThat(evento.promotorId()).isEqualTo(promotorId);
            assertThat(evento.ocorridoEm()).isNotNull();
        });
    }

    // ---- A4.a ---------------------------------------------------------------

    /**
     * A4.a — cancelar_publicaEventoCancelado_aposCommit [US-042]
     * Apos cancelar, mensagem aparece em evento.cancelado E evento.cancelado.ticket
     * (fan-out: 2 filas independentes com binding na mesma routing key).
     * VERMELHO: EventoPublisher para evento.cancelado nao existe.
     */
    @Test
    @DisplayName("A4.a — cancelar → evento.cancelado + evento.cancelado.ticket publicados")
    void cancelar_publicaEventoCancelado_aposCommit() throws Exception {
        Long promotorId = 7L;
        Long eventoId = criarEventoPublicado(promotorId);

        mvc.perform(post("/events/" + eventoId + "/cancelar")
                        .header("X-User-Id", promotorId)
                        .header("X-User-Papel", "PROMOTOR"))
                .andExpect(status().isOk());

        // Fila do payment (evento.cancelado)
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Object msgPayment = rabbitTemplate.receiveAndConvert(QUEUE_CANCELADO, 500);
            assertThat(msgPayment).isNotNull()
                    .isInstanceOf(EventoCanceladoEvent.class);
            EventoCanceladoEvent ev = (EventoCanceladoEvent) msgPayment;
            assertThat(ev.eventId()).isNotNull();
            assertThat(ev.eventoId()).isEqualTo(eventoId);
            assertThat(ev.promotorId()).isEqualTo(promotorId);
        });

        // Fila do ticket (evento.cancelado.ticket)
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Object msgTicket = rabbitTemplate.receiveAndConvert(QUEUE_CANCELADO_TK, 500);
            assertThat(msgTicket).isNotNull()
                    .isInstanceOf(EventoCanceladoEvent.class);
            EventoCanceladoEvent ev = (EventoCanceladoEvent) msgTicket;
            assertThat(ev.eventId()).isNotNull();
            assertThat(ev.eventoId()).isEqualTo(eventoId);
        });
    }

    // ---- A4.b ---------------------------------------------------------------

    /**
     * A4.b — cancelar_2x_publicaApenas1Vez [US-042]
     * 2o cancelar → 409 EVENTO_JA_CANCELADO → nenhuma 2a mensagem.
     * VERMELHO: guard de dominio ja existe em cancelar(), mas o publisher nao existe.
     */
    @Test
    @DisplayName("A4.b — cancelar 2x → 409 na 2a chamada → apenas 1 mensagem publicada")
    void cancelar_2x_publicaApenas1Vez() throws Exception {
        Long promotorId = 7L;
        Long eventoId = criarEventoPublicado(promotorId);

        // Primeira vez — ok
        mvc.perform(post("/events/" + eventoId + "/cancelar")
                        .header("X-User-Id", promotorId)
                        .header("X-User-Papel", "PROMOTOR"))
                .andExpect(status().isOk());

        // Consome a mensagem da primeira vez
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(rabbitTemplate.receiveAndConvert(QUEUE_CANCELADO, 500)).isNotNull());

        // Segunda vez — 409
        mvc.perform(post("/events/" + eventoId + "/cancelar")
                        .header("X-User-Id", promotorId)
                        .header("X-User-Papel", "PROMOTOR"))
                .andExpect(status().isConflict());

        // Aguarda e verifica que NAO ha segunda mensagem
        // (guard de dominio lancou excecao antes do afterCommit)
        await().during(2, TimeUnit.SECONDS).atMost(6, TimeUnit.SECONDS).untilAsserted(() -> {
            Object msgExtra = rabbitTemplate.receiveAndConvert(QUEUE_CANCELADO, 300);
            assertThat(msgExtra).as("Nao deve haver 2a mensagem na fila apos 2o cancelar").isNull();
        });
    }
}
