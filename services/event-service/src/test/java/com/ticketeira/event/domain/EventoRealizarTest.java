package com.ticketeira.event.domain;

import com.ticketeira.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A1 — Evento.realizar() maquina de estados (unit, H2/puro).
 * Casos: A1.a A1.b A1.c A1.d (tests-spec.md, US-043).
 * VERMELHO: Evento.realizar() ainda nao existe.
 */
@DisplayName("A1 — Evento.realizar() guard de maquina de estados")
class EventoRealizarTest {

    private Evento eventoPublicado() {
        Evento e = Evento.criar(7L, "Show da Terra", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(3),
                "Arena Norte", TipoEvento.PAGO,
                200, new java.math.BigDecimal("50.00"), 7, null);
        e.publicar();
        return e;
    }

    // ---- A1.a ---------------------------------------------------------------

    /**
     * A1.a — realizar_publicado_transicionaParaRealizado [US-043]
     * PUBLICADO -> realizar() -> status=REALIZADO, realizadoEm != null.
     */
    @Test
    @DisplayName("A1.a — PUBLICADO → realizar() → REALIZADO, realizadoEm preenchido")
    void realizar_publicado_transicionaParaRealizado() {
        Evento e = eventoPublicado();

        e.realizar();

        assertThat(e.getStatus()).isEqualTo(StatusEvento.REALIZADO);
        assertThat(e.getRealizadoEm()).isNotNull();
    }

    // ---- A1.b ---------------------------------------------------------------

    /**
     * A1.b — realizar_rascunho_lanca409 [US-043]
     * RASCUNHO -> TRANSICAO_INVALIDA (409); status inalterado.
     */
    @Test
    @DisplayName("A1.b — RASCUNHO → realizar() → 409 TRANSICAO_INVALIDA")
    void realizar_rascunho_lanca409() {
        Evento e = Evento.criar(7L, "Rascunho", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(1),
                "Local", TipoEvento.GRATUITO, 100, null, null, null);

        assertThat(e.getStatus()).isEqualTo(StatusEvento.RASCUNHO);

        assertThatThrownBy(e::realizar)
                .isInstanceOf(BusinessException.class)
                .hasMessage("TRANSICAO_INVALIDA")
                .extracting("status").isEqualTo(409);

        assertThat(e.getStatus()).isEqualTo(StatusEvento.RASCUNHO); // inalterado
    }

    // ---- A1.c ---------------------------------------------------------------

    /**
     * A1.c — realizar_cancelado_lanca409 [US-043]
     * CANCELADO -> TRANSICAO_INVALIDA (409).
     */
    @Test
    @DisplayName("A1.c — CANCELADO → realizar() → 409 TRANSICAO_INVALIDA")
    void realizar_cancelado_lanca409() {
        Evento e = Evento.criar(7L, "Cancelado", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(1),
                "Local", TipoEvento.GRATUITO, 100, null, null, null);
        e.cancelar();

        assertThatThrownBy(e::realizar)
                .isInstanceOf(BusinessException.class)
                .hasMessage("TRANSICAO_INVALIDA")
                .extracting("status").isEqualTo(409);
    }

    // ---- A1.d ---------------------------------------------------------------

    /**
     * A1.d — realizar_jaRealizado_lanca409 [US-043]
     * REALIZADO -> EVENTO_JA_REALIZADO (409) — guard de re-encerramento.
     */
    @Test
    @DisplayName("A1.d — REALIZADO → realizar() → 409 EVENTO_JA_REALIZADO")
    void realizar_jaRealizado_lanca409() {
        Evento e = eventoPublicado();
        e.realizar(); // primeira vez — ok

        assertThatThrownBy(e::realizar) // segunda vez — guard
                .isInstanceOf(BusinessException.class)
                .hasMessage("EVENTO_JA_REALIZADO")
                .extracting("status").isEqualTo(409);
    }
}
