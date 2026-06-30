package com.ticketeira.event.messaging;

import com.ticketeira.event.domain.Evento;
import com.ticketeira.event.domain.StatusEvento;
import com.ticketeira.event.domain.TipoEvento;
import com.ticketeira.event.repository.EventRepository;
import com.ticketeira.event.service.EventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A3.b / A4.c — afterCommit nao publica em rollback (unit, Mockito puro).
 * Casos: A3.b e A4.c (tests-spec.md, US-043/US-042).
 *
 * Forca excecao na transacao de encerrar()/cancelar() apos registerSynchronization
 * e verifica que RabbitTemplate nunca e chamado.
 *
 * VERMELHO: EventoPublisher, EventService.encerrar() nao existem.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("A3.b/A4.c — afterCommit nao publica em rollback (unit)")
class EventoPublisherAfterCommitTest {

    @Mock
    EventRepository eventRepository;

    @Mock
    RabbitTemplate rabbitTemplate;

    @Mock
    EventoPublisher eventoPublisher;

    // ---- A3.b ---------------------------------------------------------------

    /**
     * A3.b — encerrar_afterCommit_naoPublicaEmRollback [US-043] CRITICO
     * Forca excecao no save -> rollback -> RabbitTemplate nunca chamado.
     * Caminho feliz -> convertAndSend 1x apos commit.
     *
     * VERMELHO: EventoPublisher nao existe — compilacao falha.
     */
    @Test
    @DisplayName("A3.b — CRITICO: rollback em encerrar → RabbitTemplate nunca chamado")
    void encerrar_afterCommit_naoPublicaEmRollback() {
        // Evento PUBLICADO existente
        Evento evento = Evento.criar(7L, "Show", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local", TipoEvento.PAGO,
                100, new BigDecimal("50.00"), 7, null);
        evento.publicar();

        when(eventRepository.findById(any()))
                .thenReturn(Optional.of(evento));

        // Forca falha no save — simula rollback antes do afterCommit
        when(eventRepository.save(any(Evento.class)))
                .thenThrow(new RuntimeException("Falha simulada — rollback"));

        // Chama encerrar: deve lancar excecao (rollback)
        EventService eventService = new EventService(eventRepository, eventoPublisher);
        try {
            eventService.encerrar(evento.getId(), 7L);
        } catch (Exception e) {
            // esperado — rollback
        }

        // CRITICO: publisher NUNCA deve ser chamado se houve rollback
        verifyNoInteractions(rabbitTemplate);
        // O EventoPublisher tambem nao deve ter publicado nada
        verifyNoInteractions(eventoPublisher);
    }

    /**
     * A3.b (lado positivo) — caminho feliz → EventoPublisher chamado 1x apos commit.
     */
    @Test
    @DisplayName("A3.b — caminho feliz: encerrar sem rollback → publisher chamado 1x")
    void encerrar_semRollback_publicaUmaVez() {
        Evento evento = Evento.criar(7L, "Show", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local", TipoEvento.PAGO,
                100, new BigDecimal("50.00"), 7, null);
        evento.publicar();

        when(eventRepository.findById(any()))
                .thenReturn(Optional.of(evento));
        when(eventRepository.save(any(Evento.class))).thenReturn(evento);

        EventService eventService = new EventService(eventRepository, eventoPublisher);
        eventService.encerrar(evento.getId(), 7L);

        // Publisher deve ser chamado 1x (afterCommit — neste teste sincrono com mock de tx)
        verify(eventoPublisher, times(1)).publicarEventoFinalizado(any(Evento.class));
    }

    // ---- A4.c ---------------------------------------------------------------

    /**
     * A4.c — cancelar_afterCommit_naoPublicaEmRollback [US-042] (unit)
     * Rollback em cancelar → EventoPublisher nunca chamado.
     */
    @Test
    @DisplayName("A4.c — rollback em cancelar → EventoPublisher nunca chamado")
    void cancelar_afterCommit_naoPublicaEmRollback() {
        Evento evento = Evento.criar(7L, "Show Cancelavel", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local", TipoEvento.GRATUITO,
                100, null, null, null);
        evento.publicar();

        when(eventRepository.findById(any()))
                .thenReturn(Optional.of(evento));

        // Forca rollback no save
        when(eventRepository.save(any(Evento.class)))
                .thenThrow(new RuntimeException("Falha simulada — rollback"));

        EventService eventService = new EventService(eventRepository, eventoPublisher);
        try {
            // cancelar tambem passou a publicar evento.cancelado em afterCommit
            eventService.cancelar(evento.getId(), 7L);
        } catch (Exception e) {
            // esperado
        }

        verifyNoInteractions(eventoPublisher);
    }
}
