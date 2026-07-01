package com.ticketeira.event.service;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.common.exception.NotFoundException;
import com.ticketeira.event.domain.Evento;
import com.ticketeira.event.domain.StatusEvento;
import com.ticketeira.event.domain.TipoEvento;
import com.ticketeira.event.dto.EventoCreateRequest;
import com.ticketeira.event.dto.EventoResponse;
import com.ticketeira.event.dto.EventoUpdateRequest;
import com.ticketeira.event.dto.ReputacaoResponse;
import com.ticketeira.event.messaging.EventoPublisher;
import com.ticketeira.event.repository.AvaliacaoRepository;
import com.ticketeira.event.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventoPublisher eventoPublisher;

    @Mock
    private AvaliacaoRepository avaliacaoRepository;

    @InjectMocks
    private EventService eventService;

    private EventoCreateRequest reqGratuito;
    private EventoCreateRequest reqPago;

    @BeforeEach
    void setUp() {
        reqGratuito = new EventoCreateRequest(
                "Show Gratuito", null,
                OffsetDateTime.now().plusDays(5),
                OffsetDateTime.now().plusDays(5).plusHours(3),
                "Parque Central", TipoEvento.GRATUITO,
                100, null, null, null);

        reqPago = new EventoCreateRequest(
                "Festival Pago", "Descricao",
                OffsetDateTime.now().plusDays(10),
                OffsetDateTime.now().plusDays(10).plusHours(5),
                "Arena Norte", TipoEvento.PAGO,
                200, new BigDecimal("49.90"), 7, null);
    }

    // ---- criar ----

    @Test
    void criar_gratuito_retornaRascunhoComPromotorId() {
        Evento salvo = Evento.criar(42L, "Show Gratuito", null,
                reqGratuito.dataInicio(), reqGratuito.dataFim(),
                "Parque Central", TipoEvento.GRATUITO, 100, null, null, null);
        when(eventRepository.save(any())).thenReturn(salvo);

        Evento resultado = eventService.criar(42L, reqGratuito);

        assertThat(resultado.getStatus()).isEqualTo(StatusEvento.RASCUNHO);
        assertThat(resultado.getPromotorId()).isEqualTo(42L);
        assertThat(resultado.getVagasDisponiveis()).isNull();
        assertThat(resultado.getPreco()).isNull();
    }

    @Test
    void criar_pago_retornaRascunhoComPreco() {
        Evento salvo = Evento.criar(1L, "Festival Pago", "Descricao",
                reqPago.dataInicio(), reqPago.dataFim(),
                "Arena Norte", TipoEvento.PAGO, 200, new BigDecimal("49.90"), 7, null);
        when(eventRepository.save(any())).thenReturn(salvo);

        Evento resultado = eventService.criar(1L, reqPago);

        assertThat(resultado.getStatus()).isEqualTo(StatusEvento.RASCUNHO);
        assertThat(resultado.getPreco()).isEqualByComparingTo("49.90");
    }

    // ---- publicar ----

    @Test
    void publicar_rascunhoOwner_tornaPublicadoComVagas() {
        Evento evento = Evento.criar(1L, "Evt", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local", TipoEvento.GRATUITO, 50, null, null, null);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(evento));
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Evento resultado = eventService.publicar(1L, 10L);

        assertThat(resultado.getStatus()).isEqualTo(StatusEvento.PUBLICADO);
        assertThat(resultado.getVagasDisponiveis()).isEqualTo(50);
    }

    @Test
    void publicar_jaPublicado_lanca409() {
        Evento evento = Evento.criar(1L, "Evt", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local", TipoEvento.GRATUITO, 50, null, null, null);
        evento.publicar();
        when(eventRepository.findById(10L)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> eventService.publicar(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("EVENTO_JA_PUBLICADO");
    }

    // ---- cancelar ----

    @Test
    void cancelar_rascunhoOwner_tornaCancelado() {
        Evento evento = Evento.criar(1L, "Evt", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local", TipoEvento.GRATUITO, 50, null, null, null);
        when(eventRepository.findById(5L)).thenReturn(Optional.of(evento));
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Evento resultado = eventService.cancelar(5L, 1L);

        assertThat(resultado.getStatus()).isEqualTo(StatusEvento.CANCELADO);
    }

    @Test
    void cancelar_publicadoOwner_tornaCancelado() {
        Evento evento = Evento.criar(1L, "Evt", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local", TipoEvento.GRATUITO, 50, null, null, null);
        evento.publicar();
        when(eventRepository.findById(5L)).thenReturn(Optional.of(evento));
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Evento resultado = eventService.cancelar(5L, 1L);

        assertThat(resultado.getStatus()).isEqualTo(StatusEvento.CANCELADO);
    }

    // ---- ownership ----

    @Test
    void publicar_naoOwner_lanca404() {
        Evento evento = Evento.criar(99L, "Evt", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local", TipoEvento.GRATUITO, 50, null, null, null);
        when(eventRepository.findById(7L)).thenReturn(Optional.of(evento));

        // promotorId 1L tenta publicar evento do promotor 99L
        assertThatThrownBy(() -> eventService.publicar(1L, 7L))
                .isInstanceOf(NotFoundException.class);

        verify(eventRepository, never()).save(any());
    }

    @Test
    void editar_naoOwner_lanca404_semModificarEstado() {
        Evento evento = Evento.criar(99L, "Original", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local", TipoEvento.GRATUITO, 50, null, null, null);
        when(eventRepository.findById(7L)).thenReturn(Optional.of(evento));

        EventoUpdateRequest req = new EventoUpdateRequest(
                "Modificado", null,
                OffsetDateTime.now().plusDays(2),
                OffsetDateTime.now().plusDays(2).plusHours(2),
                "Outro Local", TipoEvento.GRATUITO,
                60, null, null, null);

        assertThatThrownBy(() -> eventService.editar(1L, 7L, req))
                .isInstanceOf(NotFoundException.class);

        assertThat(evento.getTitulo()).isEqualTo("Original");
        verify(eventRepository, never()).save(any());
    }

    @Test
    void cancelar_naoOwner_lanca404() {
        Evento evento = Evento.criar(99L, "Evt", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local", TipoEvento.GRATUITO, 50, null, null, null);
        when(eventRepository.findById(7L)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> eventService.cancelar(7L, 1L))
                .isInstanceOf(NotFoundException.class);

        verify(eventRepository, never()).save(any());
    }

    // ---- editar ----

    @Test
    void editar_rascunhoOwner_atualizaCampos() {
        Evento evento = Evento.criar(1L, "Antigo", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local A", TipoEvento.GRATUITO, 50, null, null, null);
        when(eventRepository.findById(3L)).thenReturn(Optional.of(evento));
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventoUpdateRequest req = new EventoUpdateRequest(
                "Novo Titulo", "Desc",
                OffsetDateTime.now().plusDays(2),
                OffsetDateTime.now().plusDays(2).plusHours(3),
                "Local B", TipoEvento.GRATUITO,
                80, null, null, "http://img.example.com/foto.jpg");

        Evento resultado = eventService.editar(1L, 3L, req);

        assertThat(resultado.getTitulo()).isEqualTo("Novo Titulo");
        assertThat(resultado.getLocal()).isEqualTo("Local B");
        assertThat(resultado.getCapacidade()).isEqualTo(80);
        assertThat(resultado.getStatus()).isEqualTo(StatusEvento.RASCUNHO);
    }

    @Test
    void editar_publicado_lanca409NaoEditavel() {
        Evento evento = Evento.criar(1L, "Evt", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local", TipoEvento.GRATUITO, 50, null, null, null);
        evento.publicar();
        when(eventRepository.findById(3L)).thenReturn(Optional.of(evento));

        EventoUpdateRequest req = new EventoUpdateRequest(
                "Tentativa", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local", TipoEvento.GRATUITO, 50, null, null, null);

        assertThatThrownBy(() -> eventService.editar(1L, 3L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessage("EVENTO_NAO_EDITAVEL");
    }

    // ---- detalhe ----

    @Test
    void detalhe_publicado_qualquerUserId_retornaEvento() {
        Evento evento = Evento.criar(1L, "Evt", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local", TipoEvento.GRATUITO, 50, null, null, null);
        evento.publicar();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(avaliacaoRepository.agregarReputacao(anyLong())).thenReturn(new ReputacaoResponse(null, 0L));

        EventoResponse resultado = eventService.detalhe(999L, 1L);

        assertThat(resultado.status()).isEqualTo(StatusEvento.PUBLICADO);
    }

    @Test
    void detalhe_rascunhoNaoOwner_lanca404() {
        Evento evento = Evento.criar(1L, "Evt", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local", TipoEvento.GRATUITO, 50, null, null, null);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> eventService.detalhe(999L, 1L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void detalhe_rascunhoOwner_retornaEvento() {
        Evento evento = Evento.criar(1L, "Evt", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local", TipoEvento.GRATUITO, 50, null, null, null);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(evento));
        when(avaliacaoRepository.agregarReputacao(anyLong())).thenReturn(new ReputacaoResponse(null, 0L));

        EventoResponse resultado = eventService.detalhe(1L, 1L);

        assertThat(resultado.status()).isEqualTo(StatusEvento.RASCUNHO);
    }

    @Test
    void detalhe_inexistente_lanca404() {
        when(eventRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.detalhe(1L, 99L))
                .isInstanceOf(NotFoundException.class);
    }
}
