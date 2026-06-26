package com.ticketeira.event.domain;

import com.ticketeira.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventoTest {

    private Evento eventoGratuito() {
        return Evento.criar(1L, "Show da Terra", null,
                OffsetDateTime.now().plusDays(5),
                OffsetDateTime.now().plusDays(5).plusHours(3),
                "Parque Central", TipoEvento.GRATUITO,
                100, null, null, null);
    }

    private Evento eventoPago() {
        return Evento.criar(1L, "Festival Pago", null,
                OffsetDateTime.now().plusDays(10),
                OffsetDateTime.now().plusDays(10).plusHours(5),
                "Arena Norte", TipoEvento.PAGO,
                200, new BigDecimal("49.90"), 7, null);
    }

    @Test
    void criarEvento_inicializaStatusRascunho() {
        Evento e = eventoGratuito();
        assertThat(e.getStatus()).isEqualTo(StatusEvento.RASCUNHO);
    }

    @Test
    void criarEvento_vagasDisponiveisNulaAtePublicar() {
        Evento e = eventoGratuito();
        assertThat(e.getVagasDisponiveis()).isNull();
    }

    @Test
    void publicar_rascunho_tornaPUBLICADO_eInicializaVagas() {
        Evento e = eventoGratuito();
        e.publicar();
        assertThat(e.getStatus()).isEqualTo(StatusEvento.PUBLICADO);
        assertThat(e.getVagasDisponiveis()).isEqualTo(e.getCapacidade());
    }

    @Test
    void publicar_jaPublicado_lanca409() {
        Evento e = eventoGratuito();
        e.publicar();
        assertThatThrownBy(e::publicar)
                .isInstanceOf(BusinessException.class)
                .hasMessage("EVENTO_JA_PUBLICADO")
                .extracting("status").isEqualTo(409);
    }

    @Test
    void publicar_cancelado_lanca409TransicaoInvalida() {
        Evento e = eventoGratuito();
        e.cancelar();
        assertThatThrownBy(e::publicar)
                .isInstanceOf(BusinessException.class)
                .hasMessage("TRANSICAO_INVALIDA")
                .extracting("status").isEqualTo(409);
    }

    @Test
    void cancelar_rascunho_tornaCancelado() {
        Evento e = eventoGratuito();
        e.cancelar();
        assertThat(e.getStatus()).isEqualTo(StatusEvento.CANCELADO);
    }

    @Test
    void cancelar_publicado_tornaCancelado() {
        Evento e = eventoGratuito();
        e.publicar();
        e.cancelar();
        assertThat(e.getStatus()).isEqualTo(StatusEvento.CANCELADO);
    }

    @Test
    void cancelar_jaCancelado_lanca409() {
        Evento e = eventoGratuito();
        e.cancelar();
        assertThatThrownBy(e::cancelar)
                .isInstanceOf(BusinessException.class)
                .hasMessage("EVENTO_JA_CANCELADO")
                .extracting("status").isEqualTo(409);
    }

    @Test
    void atualizarDados_rascunho_atualizaCampos() {
        Evento e = eventoGratuito();
        e.atualizarDados("Novo Titulo", "Descricao nova",
                OffsetDateTime.now().plusDays(7),
                OffsetDateTime.now().plusDays(7).plusHours(2),
                "Novo Local", TipoEvento.GRATUITO,
                150, null, null, "http://img.example.com/x.jpg");
        assertThat(e.getTitulo()).isEqualTo("Novo Titulo");
        assertThat(e.getLocal()).isEqualTo("Novo Local");
        assertThat(e.getCapacidade()).isEqualTo(150);
        assertThat(e.getStatus()).isEqualTo(StatusEvento.RASCUNHO);
    }

    @Test
    void atualizarDados_publicado_lanca409NaoEditavel() {
        Evento e = eventoGratuito();
        e.publicar();
        assertThatThrownBy(() -> e.atualizarDados("X", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(1),
                "Local", TipoEvento.GRATUITO, 10, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("EVENTO_NAO_EDITAVEL");
    }
}
