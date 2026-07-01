package com.ticketeira.ticket.domain;

import com.ticketeira.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A1 — Unit POJO de Ingresso.realizarCheckin() (US-034).
 * Casos: A1.a A1.b A1.c A1.d
 *
 * VERMELHO: Ingresso.realizarCheckin() nao existe ainda.
 */
@Tag("unit")
@DisplayName("A1 — Ingresso.realizarCheckin() transicoes de estado")
class IngressoRealizarCheckinTest {

    // Helpers

    private Ingresso ingressoAtivo(Long inscricaoId) {
        return Ingresso.emitir(inscricaoId);   // status = ATIVO
    }

    private Ingresso ingressoUtilizado(Long inscricaoId) {
        Ingresso ing = Ingresso.emitir(inscricaoId);
        ing.utilizar();   // no-op idempotente da 5A -> UTILIZADO
        return ing;
    }

    private Ingresso ingressoCancelado(Long inscricaoId) {
        Ingresso ing = Ingresso.emitir(inscricaoId);
        ing.cancelar();   // -> CANCELADO
        return ing;
    }

    // A1.a -------------------------------------------------------------------

    /**
     * A1.a — realizarCheckin em ingresso ATIVO transiciona para UTILIZADO. [US-034]
     */
    @Test
    @DisplayName("A1.a — ATIVO -> realizarCheckin() -> UTILIZADO")
    void realizarCheckin_ingressoAtivo_ficaUtilizado() {
        Ingresso ing = ingressoAtivo(1L);

        ing.realizarCheckin();

        assertThat(ing.getStatus()).isEqualTo(StatusIngresso.UTILIZADO);
    }

    // A1.b -------------------------------------------------------------------

    /**
     * A1.b — realizarCheckin em ingresso ja UTILIZADO lanca BusinessException 409
     * INGRESSO_JA_UTILIZADO. [US-034.2]
     */
    @Test
    @DisplayName("A1.b — UTILIZADO -> realizarCheckin() -> 409 INGRESSO_JA_UTILIZADO")
    void realizarCheckin_ingressoJaUtilizado_lanca409() {
        Ingresso ing = ingressoUtilizado(1L);

        assertThatThrownBy(ing::realizarCheckin)
                .isInstanceOf(BusinessException.class)
                .hasMessage("INGRESSO_JA_UTILIZADO")
                .extracting("status")
                .isEqualTo(409);
    }

    // A1.c -------------------------------------------------------------------

    /**
     * A1.c — realizarCheckin em ingresso CANCELADO lanca 409 (nao reaproveita). [US-034.4]
     */
    @Test
    @DisplayName("A1.c — CANCELADO -> realizarCheckin() -> 409")
    void realizarCheckin_ingressoCancelado_lanca409() {
        Ingresso ing = ingressoCancelado(1L);

        assertThatThrownBy(ing::realizarCheckin)
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(409);
    }

    // A1.d -------------------------------------------------------------------

    /**
     * A1.d — utilizar() (5A) permanece no-op idempotente; nao quebrou. [US-034 / regressao 5A]
     */
    @Test
    @DisplayName("A1.d — utilizar() 5A preservado: ATIVO->UTILIZADO no-op em 2a chamada")
    void utilizar_permanece_noOpIdempotente() {
        Ingresso ing = ingressoAtivo(2L);

        ing.utilizar();
        assertThat(ing.getStatus()).isEqualTo(StatusIngresso.UTILIZADO);

        // 2a chamada: no-op (nao lanca)
        ing.utilizar();
        assertThat(ing.getStatus()).isEqualTo(StatusIngresso.UTILIZADO);
    }
}
