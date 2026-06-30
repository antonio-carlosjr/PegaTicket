package com.ticketeira.payment.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit — Pagamento.repassar() e Pagamento.reembolsar() (data-model.md §A).
 * Nao precisa de Spring/banco — logica de dominio puro.
 * VERMELHO: repassar()/reembolsar()/pendente(eventoId/promotorId) ainda nao existem.
 */
@DisplayName("Pagamento — repassar() / reembolsar() / factory com eventoId/promotorId")
class PagamentoRepassarReembolsarTest {

    private Pagamento pagamentoConfirmado(Long eventoId, Long promotorId) {
        Pagamento p = Pagamento.pendente(1L, 10L, eventoId, promotorId,
                new BigDecimal("100.00"), new BigDecimal("0.1000"));
        p.confirmar("SIM-123");
        return p;
    }

    // ---- repassar() ---------------------------------------------------------

    @Test
    @DisplayName("repassar: CONFIRMADO → REPASSADO, repassadoEm preenchido, retorna true")
    void repassar_confirmado_transicionaParaRepassado() {
        Pagamento p = pagamentoConfirmado(10L, 7L);

        boolean resultado = p.repassar();

        assertThat(resultado).isTrue();
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.REPASSADO);
        assertThat(p.getRepassadoEm()).isNotNull();
    }

    @Test
    @DisplayName("repassar: nao-CONFIRMADO → no-op, retorna false, status inalterado")
    void repassar_naoCOnfirmado_noOp() {
        // PENDENTE nao deve ser repassado
        Pagamento p = Pagamento.pendente(2L, 11L, 10L, 7L,
                new BigDecimal("100.00"), new BigDecimal("0.1000"));

        boolean resultado = p.repassar();

        assertThat(resultado).isFalse();
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(p.getRepassadoEm()).isNull();
    }

    @Test
    @DisplayName("repassar: ja REPASSADO → no-op (idempotente)")
    void repassar_jaRepassado_noOp() {
        Pagamento p = pagamentoConfirmado(10L, 7L);
        p.repassar(); // primeira vez

        boolean resultado = p.repassar(); // segunda vez

        assertThat(resultado).isFalse();
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.REPASSADO);
    }

    // ---- reembolsar() -------------------------------------------------------

    @Test
    @DisplayName("reembolsar: CONFIRMADO → REEMBOLSADO, reembolsadoEm preenchido, retorna true")
    void reembolsar_confirmado_transicionaParaReembolsado() {
        Pagamento p = pagamentoConfirmado(10L, 7L);

        boolean resultado = p.reembolsar();

        assertThat(resultado).isTrue();
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.REEMBOLSADO);
        assertThat(p.getReembolsadoEm()).isNotNull();
    }

    @Test
    @DisplayName("reembolsar: nao-CONFIRMADO → no-op, retorna false")
    void reembolsar_naoCOnfirmado_noOp() {
        Pagamento p = Pagamento.pendente(3L, 12L, 10L, 7L,
                new BigDecimal("100.00"), new BigDecimal("0.1000"));

        boolean resultado = p.reembolsar();

        assertThat(resultado).isFalse();
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
    }

    // ---- factory pendente com eventoId/promotorId (TECH-S4-01) --------------

    @Test
    @DisplayName("TECH-S4-01 — pendente com eventoId/promotorId grava os campos")
    void pendente_comEventoIdEPromotorId_persisteIds() {
        Long eventoId = 42L;
        Long promotorId = 99L;

        Pagamento p = Pagamento.pendente(5L, 20L, eventoId, promotorId,
                new BigDecimal("150.00"), new BigDecimal("0.1000"));

        assertThat(p.getEventoId()).isEqualTo(eventoId);
        assertThat(p.getPromotorId()).isEqualTo(promotorId);
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(p.getValorBruto()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(p.getValorTaxa()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(p.getValorRepasse()).isEqualByComparingTo(new BigDecimal("135.00"));
    }
}
