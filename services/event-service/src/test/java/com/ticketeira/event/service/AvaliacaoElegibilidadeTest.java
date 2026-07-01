package com.ticketeira.event.service;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.event.client.TicketClient;
import com.ticketeira.event.domain.Evento;
import com.ticketeira.event.domain.StatusEvento;
import com.ticketeira.event.domain.TipoEvento;
import com.ticketeira.event.repository.AvaliacaoRepository;
import com.ticketeira.event.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D2 — Unit do AvaliacaoService: pre-filtro de elegibilidade. [US-024]
 * Casos: D2.a D2.b
 *
 * VERMELHO: AvaliacaoService, TicketClient nao existem.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("D2 — AvaliacaoService elegibilidade (pre-filtro + resiliencia)")
class AvaliacaoElegibilidadeTest {

    @Mock
    EventRepository eventRepository;

    @Mock
    AvaliacaoRepository avaliacaoRepository;

    @Mock
    TicketClient ticketClient;

    AvaliacaoService avaliacaoService;

    private static final Long USUARIO_ID = 10L;
    private static final Long EVENTO_ID = 42L;

    @BeforeEach
    void setUp() {
        avaliacaoService = new AvaliacaoService(eventRepository, avaliacaoRepository, ticketClient);
    }

    private Evento eventoRealizado() {
        Evento e = Evento.criar(5L, "Show", null,
                OffsetDateTime.now().minusDays(5),
                OffsetDateTime.now().minusDays(5).plusHours(3),
                "Arena", TipoEvento.PAGO, 100, new BigDecimal("50.00"), 7, null);
        e.publicar();
        e.realizar();
        return e;
    }

    private Evento eventoPublicado() {
        Evento e = Evento.criar(5L, "Show Futuro", null,
                OffsetDateTime.now().plusDays(30),
                OffsetDateTime.now().plusDays(30).plusHours(3),
                "Arena", TipoEvento.GRATUITO, 100, null, null, null);
        e.publicar();
        return e;
    }

    // D2.a -------------------------------------------------------------------

    /**
     * D2.a — chama ticketClient.participou APENAS quando evento.status==REALIZADO;
     * pre-filtro evita round-trip para evento nao REALIZADO. [US-024.3 / perf]
     */
    @Test
    @DisplayName("D2.a — pre-filtro: evento nao REALIZADO -> NAO chama ticketClient")
    void avaliar_eventoNaoRealizado_naoChama_ticketClient() {
        Evento publicado = eventoPublicado();
        when(eventRepository.findById(EVENTO_ID)).thenReturn(Optional.of(publicado));

        assertThatThrownBy(() -> avaliacaoService.avaliar(EVENTO_ID, USUARIO_ID, 4, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("AVALIACAO_NAO_ELEGIVEL");

        // CRITICO: TicketClient NAO deve ser chamado (pre-filtro local evita round-trip)
        verify(ticketClient, never()).participou(anyLong(), anyLong());
    }

    // D2.b -------------------------------------------------------------------

    /**
     * D2.b — ticketClient retorna 503/erro -> AvaliacaoService propaga TICKET_INDISPONIVEL (503), nunca 500. [US-024 / resiliencia]
     */
    @Test
    @DisplayName("D2.b — ticketClient falha -> 503 TICKET_INDISPONIVEL (nunca 500)")
    void avaliar_ticketClientFalha_propaga503() {
        Evento realizado = eventoRealizado();
        when(eventRepository.findById(EVENTO_ID)).thenReturn(Optional.of(realizado));
        when(ticketClient.participou(anyLong(), anyLong()))
                .thenThrow(new RuntimeException("Timeout ao conectar com ticket-service"));

        assertThatThrownBy(() -> avaliacaoService.avaliar(EVENTO_ID, USUARIO_ID, 4, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("TICKET_INDISPONIVEL")
                .extracting("status").isEqualTo(503);
    }
}
