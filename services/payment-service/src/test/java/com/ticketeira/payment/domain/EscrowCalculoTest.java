package com.ticketeira.payment.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitarios do calculo de escrow (B3).
 * Verifica RoundingMode.HALF_UP, scale=2 e a formula: taxa=round(bruto*0.10,2), repasse=bruto-taxa.
 * Nao precisa de Spring/banco; puro dominio.
 * Caso de teste: B3 (tests-spec.md)
 */
@DisplayName("B3 — Calculo de escrow (taxa 10%, HALF_UP, scale 2)")
class EscrowCalculoTest {

    private static final BigDecimal TAXA_PERCENTUAL = new BigDecimal("0.1000");

    /**
     * Replica a formula que sera implementada em Pagamento.pendente() / PagamentoService.
     */
    private BigDecimal calcularTaxa(BigDecimal bruto) {
        return bruto.multiply(TAXA_PERCENTUAL).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcularRepasse(BigDecimal bruto) {
        return bruto.subtract(calcularTaxa(bruto));
    }

    @ParameterizedTest(name = "bruto={0} -> taxa={1}, repasse={2}")
    @CsvSource({
            "100.00, 10.00, 90.00",   // caso basico
            "99.99,  10.00, 89.99",   // round(9.999, HALF_UP) = 10.00 -> repasse=89.99
            "0.01,   0.00,  0.01",    // round(0.001, HALF_UP) = 0.00
            "33.33,  3.33,  30.00",   // round(3.333, HALF_UP) = 3.33
            "33.35,  3.34,  30.01",   // round(3.335, HALF_UP) = 3.34
            "1000.00, 100.00, 900.00" // valor alto
    })
    @DisplayName("escrow_arredondamento")
    void escrow_arredondamento(BigDecimal bruto, BigDecimal taxaEsperada, BigDecimal repasseEsperado) {
        BigDecimal taxa = calcularTaxa(bruto);
        BigDecimal repasse = calcularRepasse(bruto);

        assertThat(taxa)
                .as("valorTaxa para bruto=%s", bruto)
                .isEqualByComparingTo(taxaEsperada);

        assertThat(repasse)
                .as("valorRepasse para bruto=%s", bruto)
                .isEqualByComparingTo(repasseEsperado);

        // Invariante: bruto = taxa + repasse
        assertThat(taxa.add(repasse))
                .as("taxa + repasse deve ser igual ao bruto")
                .isEqualByComparingTo(bruto);
    }
}
