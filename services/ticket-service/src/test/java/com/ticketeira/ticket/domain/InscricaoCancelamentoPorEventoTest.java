package com.ticketeira.ticket.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit — Inscricao.cancelarPorEvento() e Ingresso.cancelar() (data-model.md §C).
 * Nao precisa de Spring/banco — logica de dominio puro.
 * VERMELHO: cancelarPorEvento() e Ingresso.cancelar() ainda nao existem.
 */
@DisplayName("Inscricao.cancelarPorEvento() e Ingresso.cancelar() — unit")
class InscricaoCancelamentoPorEventoTest {

    // ---- Inscricao.cancelarPorEvento() --------------------------------------

    @Test
    @DisplayName("cancelarPorEvento: ATIVA → CANCELADA, retorna true")
    void cancelarPorEvento_ativa_tornaCancelada() {
        Inscricao i = Inscricao.criar(10L, 100L);
        assertThat(i.getStatus()).isEqualTo(StatusInscricao.ATIVA);

        boolean resultado = i.cancelarPorEvento();

        assertThat(resultado).isTrue();
        assertThat(i.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
    }

    @Test
    @DisplayName("cancelarPorEvento: PENDENTE_PAGAMENTO → CANCELADA, retorna true")
    void cancelarPorEvento_pendentePagamento_tornaCancelada() {
        Inscricao i = Inscricao.pendentePagamento(10L, 100L);
        assertThat(i.getStatus()).isEqualTo(StatusInscricao.PENDENTE_PAGAMENTO);

        boolean resultado = i.cancelarPorEvento();

        assertThat(resultado).isTrue();
        assertThat(i.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
    }

    @Test
    @DisplayName("cancelarPorEvento: ja CANCELADA → no-op, retorna false (idempotente)")
    void cancelarPorEvento_jaCancelada_noOp() {
        Inscricao i = Inscricao.criar(10L, 100L);
        i.cancelarPorEvento(); // primeira vez

        boolean resultado = i.cancelarPorEvento(); // segunda vez

        assertThat(resultado).isFalse();
        assertThat(i.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
    }

    @Test
    @DisplayName("cancelarPorEvento: EXPIRADA → no-op, retorna false (sem lancar excecao)")
    void cancelarPorEvento_expirada_noOp() {
        Inscricao i = Inscricao.pendentePagamento(10L, 100L);
        i.expirar(); // → EXPIRADA

        // NAO deve lancar excecao (diferente do cancelar() original que lancaria)
        boolean resultado = i.cancelarPorEvento();

        assertThat(resultado).isFalse();
        assertThat(i.getStatus()).isEqualTo(StatusInscricao.EXPIRADA);
    }

    // ---- Ingresso.cancelar() ------------------------------------------------

    @Test
    @DisplayName("Ingresso.cancelar: ATIVO → CANCELADO, retorna true")
    void ingresso_cancelar_ativo_tornaCancelado() {
        Ingresso ing = Ingresso.emitir(1L); // factory existente (usa inscricaoId)

        boolean resultado = ing.cancelar();

        assertThat(resultado).isTrue();
        assertThat(ing.getStatus()).isEqualTo(StatusIngresso.CANCELADO);
    }

    @Test
    @DisplayName("Ingresso.cancelar: ja CANCELADO → no-op, retorna false (idempotente)")
    void ingresso_cancelar_jaCancelado_noOp() {
        Ingresso ing = Ingresso.emitir(1L);
        ing.cancelar(); // primeira vez

        boolean resultado = ing.cancelar(); // segunda vez

        assertThat(resultado).isFalse();
        assertThat(ing.getStatus()).isEqualTo(StatusIngresso.CANCELADO);
    }

    @Test
    @DisplayName("Ingresso.cancelar: UTILIZADO → no-op, retorna false (preserva historico de check-in)")
    void ingresso_cancelar_utilizado_noOp() {
        Ingresso ing = Ingresso.emitir(1L);
        // Simula check-in (status UTILIZADO)
        ing.utilizar(); // metodo de check-in — VERMELHO se nao existir

        boolean resultado = ing.cancelar();

        assertThat(resultado).isFalse();
        assertThat(ing.getStatus()).isEqualTo(StatusIngresso.UTILIZADO);
    }
}
