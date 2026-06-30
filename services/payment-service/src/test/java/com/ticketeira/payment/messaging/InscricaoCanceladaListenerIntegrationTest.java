package com.ticketeira.payment.messaging;

import com.ticketeira.payment.TestcontainersBase;
import com.ticketeira.payment.domain.Pagamento;
import com.ticketeira.payment.domain.StatusPagamento;
import com.ticketeira.payment.repository.PagamentoRepository;
import com.ticketeira.payment.repository.ProcessedEventRepository;
import com.ticketeira.payment.repository.ReembolsoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * C1 — payment-service: reembolso individual pos inscricao.cancelada. [US-035 / US-042 individual] CRITICO
 * Casos: C1.a C1.b C1.c C1.d C1.e
 *
 * Gabarito: EventoCanceladoListenerIntegrationTest (5A).
 * @BeforeEach purga inscricao.cancelada + limpa repos (aprendizado S4).
 *
 * VERMELHO: InscricaoCanceladaListener, InscricaoCanceladaEvent, fila inscricao.cancelada nao existem.
 */
@Tag("integracao")
@SpringBootTest
@ActiveProfiles("test-postgres")
@DisplayName("C1 — Reembolso individual pos inscricao.cancelada (Testcontainers PG+Rabbit)")
class InscricaoCanceladaListenerIntegrationTest extends TestcontainersBase {

    private static final String EXCHANGE                 = "ticketeira.events";
    private static final String RK_INSCRICAO_CANCELADA   = "inscricao.cancelada";
    private static final String QUEUE_INSCRICAO_CANCELADA = "inscricao.cancelada";
    private static final String QUEUE_CANCELADO           = "evento.cancelado";
    private static final String QUEUE_FINALIZADO          = "evento.finalizado";

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    RabbitAdmin rabbitAdmin;

    @Autowired
    PagamentoRepository pagamentoRepository;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @Autowired
    ReembolsoRepository reembolsoRepository;

    @BeforeEach
    void limpar() {
        reembolsoRepository.deleteAll();
        pagamentoRepository.deleteAll();
        processedEventRepository.deleteAll();
        // Aprendizado S4/5A: purgar TODAS as filas relevantes para isolamento
        rabbitAdmin.purgeQueue(QUEUE_INSCRICAO_CANCELADA, false);
        rabbitAdmin.purgeQueue(QUEUE_CANCELADO, false);
        rabbitAdmin.purgeQueue(QUEUE_FINALIZADO, false);
    }

    /** Seed: Pagamento CONFIRMADO para inscricao. */
    private Pagamento seedConfirmado(Long inscricaoId, Long usuarioId, Long eventoId,
                                     Long promotorId, String valor) {
        var p = Pagamento.pendente(
                inscricaoId, usuarioId, eventoId, promotorId,
                new BigDecimal(valor), new BigDecimal("0.1000"));
        p.confirmar("SIM-" + inscricaoId);
        return pagamentoRepository.saveAndFlush(p);
    }

    /** Seed: Pagamento PENDENTE (nunca confirmado). */
    private void seedPendente(Long inscricaoId, Long usuarioId, Long eventoId) {
        var p = Pagamento.pendente(
                inscricaoId, usuarioId, eventoId, 5L,
                new BigDecimal("100.00"), new BigDecimal("0.1000"));
        pagamentoRepository.saveAndFlush(p);
    }

    /** Seed: Pagamento ja REEMBOLSADO (status nao-CONFIRMADO). */
    private void seedReembolsado(Long inscricaoId, Long usuarioId, Long eventoId) {
        var p = Pagamento.pendente(
                inscricaoId, usuarioId, eventoId, 5L,
                new BigDecimal("100.00"), new BigDecimal("0.1000"));
        p.confirmar("SIM-" + inscricaoId);
        p.reembolsar();   // CONFIRMADO -> REEMBOLSADO
        pagamentoRepository.saveAndFlush(p);
    }

    /** Publica evento inscricao.cancelada. */
    private UUID publicarInscricaoCancelada(Long inscricaoId, Long usuarioId, Long eventoId) {
        UUID eventId = UUID.randomUUID();
        InscricaoCanceladaEvent msg = new InscricaoCanceladaEvent(
                eventId, inscricaoId, usuarioId, eventoId, OffsetDateTime.now());
        rabbitTemplate.convertAndSend(EXCHANGE, RK_INSCRICAO_CANCELADA, msg);
        return eventId;
    }

    // C1.a -------------------------------------------------------------------

    /**
     * C1.a — CRITICO: pagamento CONFIRMADO da inscricaoId -> publica inscricao.cancelada ->
     * pagamento REEMBOLSADO + 1 reembolsos(motivo=CANCELAMENTO_PARTICIPANTE, status=PROCESSADO)
     * + 1 processed_event. [US-035.2]
     */
    @Test
    @DisplayName("C1.a — CRITICO: inscricao.cancelada -> CONFIRMADO->REEMBOLSADO + reembolso CANCELAMENTO_PARTICIPANTE")
    void inscricaoCancelada_pagamentoConfirmado_reembolsa() {
        Long inscricaoId = 1L;
        Long usuarioId = 101L;
        Long eventoId = 42L;

        seedConfirmado(inscricaoId, usuarioId, eventoId, 5L, "150.00");

        UUID eventId = publicarInscricaoCancelada(inscricaoId, usuarioId, eventoId);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            // Pagamento REEMBOLSADO
            var p = pagamentoRepository.findByInscricaoId(inscricaoId).orElseThrow();
            assertThat(p.getStatus())
                    .as("Pagamento deve ser REEMBOLSADO")
                    .isEqualTo(StatusPagamento.REEMBOLSADO);

            // 1 reembolso com motivo=CANCELAMENTO_PARTICIPANTE
            var reembolsos = reembolsoRepository.findAll();
            assertThat(reembolsos).hasSize(1);
            assertThat(reembolsos.get(0).getMotivo()).isEqualTo("CANCELAMENTO_PARTICIPANTE");
            assertThat(reembolsos.get(0).getStatus()).isEqualTo("PROCESSADO");
            assertThat(reembolsos.get(0).getValor())
                    .as("Valor do reembolso = valorBruto do pagamento")
                    .isEqualByComparingTo(new BigDecimal("150.00"));

            // 1 processed_event
            assertThat(processedEventRepository.existsById(eventId)).isTrue();
        });
    }

    // C1.b -------------------------------------------------------------------

    /**
     * C1.b — CRITICO: reentrega 2x da mesma mensagem -> 1 reembolso apenas; pagamento REEMBOLSADO 1x;
     * processed_events.count=1. [US-035 / ADR-T11]
     */
    @Test
    @DisplayName("C1.b — CRITICO: reentrega -> idempotente (1 reembolso, 1 processed_event)")
    void inscricaoCancelada_reentrega_idempotente() {
        Long inscricaoId = 2L;
        Long usuarioId = 102L;
        Long eventoId = 42L;

        seedConfirmado(inscricaoId, usuarioId, eventoId, 5L, "100.00");

        UUID eventId = UUID.randomUUID();
        InscricaoCanceladaEvent msg = new InscricaoCanceladaEvent(
                eventId, inscricaoId, usuarioId, eventoId, OffsetDateTime.now());

        // 1a entrega
        rabbitTemplate.convertAndSend(EXCHANGE, RK_INSCRICAO_CANCELADA, msg);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(reembolsoRepository.count()).isEqualTo(1));

        // 2a entrega (reentrega com mesmo eventId)
        rabbitTemplate.convertAndSend(EXCHANGE, RK_INSCRICAO_CANCELADA, msg);

        await().during(3, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // CRITICO: ainda 1 reembolso (nao 2)
            assertThat(reembolsoRepository.count())
                    .as("Reentrega nao deve criar reembolso duplicado").isEqualTo(1);
            // 1 processed_event
            assertThat(processedEventRepository.count())
                    .as("1 processed_event na reentrega").isEqualTo(1);
        });
    }

    // C1.c -------------------------------------------------------------------

    /**
     * C1.c — pagamento ja REEMBOLSADO (nao-CONFIRMADO) -> ACK no-op, nenhum novo reembolso. [US-035 / CR-S4-01]
     */
    @Test
    @DisplayName("C1.c — pagamento ja REEMBOLSADO -> no-op (CR-S4-01)")
    void inscricaoCancelada_pagamentoJaReembolsado_noOp() {
        Long inscricaoId = 3L;
        seedReembolsado(inscricaoId, 103L, 42L);

        UUID eventId = publicarInscricaoCancelada(inscricaoId, 103L, 42L);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(processedEventRepository.existsById(eventId)).isTrue());

        // Nenhum reembolso novo (pagamento ja estava REEMBOLSADO)
        assertThat(reembolsoRepository.count())
                .as("Nenhum reembolso adicional para pagamento ja REEMBOLSADO")
                .isZero();
    }

    // C1.d -------------------------------------------------------------------

    /**
     * C1.d — inscricaoId sem pagamento (evento gratuito; defesa) -> ACK no-op,
     * processed_event gravado, 0 reembolsos, sem DLQ. [US-035.1 / defesa]
     */
    @Test
    @DisplayName("C1.d — inscricaoId sem pagamento (GRATUITO) -> no-op, sem DLQ")
    void inscricaoCancelada_semPagamento_noOp() {
        Long inscricaoId = 99L;   // nao tem pagamento no banco

        UUID eventId = publicarInscricaoCancelada(inscricaoId, 199L, 42L);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(processedEventRepository.existsById(eventId)).isTrue());

        assertThat(reembolsoRepository.count()).as("0 reembolsos para inscricao sem pagamento").isZero();
        assertThat(pagamentoRepository.count()).isZero();
    }

    // C1.e -------------------------------------------------------------------

    /**
     * C1.e — pagamento PENDENTE (nunca confirmado) -> no-op (so CONFIRMADO reembolsa). [US-035 / borda]
     */
    @Test
    @DisplayName("C1.e — pagamento PENDENTE -> no-op (nao reembolsa)")
    void inscricaoCancelada_pagamentoPendente_noOp() {
        Long inscricaoId = 4L;
        seedPendente(inscricaoId, 104L, 42L);

        UUID eventId = publicarInscricaoCancelada(inscricaoId, 104L, 42L);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(processedEventRepository.existsById(eventId)).isTrue());

        // Pagamento deve continuar PENDENTE
        var p = pagamentoRepository.findAll().stream()
                .filter(pg -> inscricaoId.equals(pg.getInscricaoId()))
                .findFirst().orElseThrow();
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(reembolsoRepository.count()).isZero();
    }
}
