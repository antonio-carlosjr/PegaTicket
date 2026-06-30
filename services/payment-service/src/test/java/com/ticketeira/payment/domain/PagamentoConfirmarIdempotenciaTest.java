package com.ticketeira.payment.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitarios de Pagamento.confirmar() — idempotencia (B2.c parcial, B3 dominio).
 * Verifica que:
 * - primeira chamada retorna true e transiciona para CONFIRMADO;
 * - segunda chamada retorna false (no-op), nao deve re-publicar evento.
 * Caso de teste: B2.c (confirmar_2x_idempotente_naoRepublica — a parte de "nao republica" fica no B4)
 */
@DisplayName("Pagamento.confirmar() — idempotencia de transicao")
class PagamentoConfirmarIdempotenciaTest {

    private static final BigDecimal TAXA_PERCENTUAL = new BigDecimal("0.1000");

    @Test
    @DisplayName("confirmar_pendente_retornaTrue_eTransicionaParaConfirmado")
    void confirmar_pendente_retornaTrue_eTransicionaParaConfirmado() {
        Pagamento pagamento = Pagamento.pendente(1L, 10L, new BigDecimal("100.00"), TAXA_PERCENTUAL);

        boolean transicionou = pagamento.confirmar("SIM-abc123");

        assertThat(transicionou).isTrue();
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.CONFIRMADO);
        assertThat(pagamento.getGatewayPaymentId()).isEqualTo("SIM-abc123");
        assertThat(pagamento.getProcessadoEm()).isNotNull();
    }

    @Test
    @DisplayName("confirmar_2x_segundaVezRetornaFalse_semMudarEstado")
    void confirmar_2x_segundaVezRetornaFalse_semMudarEstado() {
        Pagamento pagamento = Pagamento.pendente(1L, 10L, new BigDecimal("100.00"), TAXA_PERCENTUAL);
        pagamento.confirmar("SIM-primeira");

        // Segunda chamada: idempotente -> false
        boolean transicionou = pagamento.confirmar("SIM-segunda");

        assertThat(transicionou).isFalse();
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.CONFIRMADO);
        // gatewayPaymentId nao muda
        assertThat(pagamento.getGatewayPaymentId()).isEqualTo("SIM-primeira");
    }

    @Test
    @DisplayName("pendente_computaEscrow_corretamente")
    void pendente_computaEscrow_corretamente() {
        BigDecimal bruto = new BigDecimal("100.00");
        Pagamento pagamento = Pagamento.pendente(1L, 10L, bruto, TAXA_PERCENTUAL);

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(pagamento.getValorBruto()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(pagamento.getValorTaxa()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(pagamento.getValorRepasse()).isEqualByComparingTo(new BigDecimal("90.00"));
        assertThat(pagamento.getGatewayPaymentId()).isNull();
        assertThat(pagamento.getProcessadoEm()).isNull();
    }

    @Test
    @DisplayName("pendente_arredondamento9999_taxa1000_repasse8999")
    void pendente_arredondamento9999_taxa1000_repasse8999() {
        // bruto 99.99 -> round(9.999, HALF_UP)=10.00, repasse=89.99
        Pagamento pagamento = Pagamento.pendente(2L, 20L, new BigDecimal("99.99"), TAXA_PERCENTUAL);

        assertThat(pagamento.getValorTaxa()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(pagamento.getValorRepasse()).isEqualByComparingTo(new BigDecimal("89.99"));
    }
}
