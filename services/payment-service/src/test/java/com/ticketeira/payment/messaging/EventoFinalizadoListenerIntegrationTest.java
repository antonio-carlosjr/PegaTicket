package com.ticketeira.payment.messaging;

import com.ticketeira.payment.TestcontainersBase;
import com.ticketeira.payment.domain.StatusPagamento;
import com.ticketeira.payment.repository.PagamentoRepository;
import com.ticketeira.payment.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
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
 * B2 — payment-service: repasse pos evento.finalizado.
 * Casos: B2.a B2.b B2.c B2.d B2.e (tests-spec.md, US-043/TECH-S4-01).
 * Requer Testcontainers Postgres + RabbitMQ reais.
 *
 * VERMELHO: EventoFinalizadoListener, RepasseService, Pagamento.repassar(),
 * campos eventoId/promotorId em Pagamento ainda nao existem.
 */
@Tag("integracao")
@SpringBootTest
@ActiveProfiles("test-postgres")
@DisplayName("B2 — Repasse pos evento.finalizado (Testcontainers)")
class EventoFinalizadoListenerIntegrationTest extends TestcontainersBase {

    private static final String EXCHANGE           = "ticketeira.events";
    private static final String RK_FINALIZADO      = "evento.finalizado";
    private static final String QUEUE_FINALIZADO   = "evento.finalizado";
    private static final String QUEUE_CANCELADO    = "evento.cancelado";

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    RabbitAdmin rabbitAdmin;

    @Autowired
    PagamentoRepository pagamentoRepository;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @BeforeEach
    void limpar() {
        pagamentoRepository.deleteAll();
        processedEventRepository.deleteAll();
        // Aprendizado S4: purgar filas para isolamento entre testes que compartilham broker.
        rabbitAdmin.purgeQueue(QUEUE_FINALIZADO, false);
        rabbitAdmin.purgeQueue(QUEUE_CANCELADO, false);
    }

    /** Seed: cria pagamento CONFIRMADO no evento + salva. */
    private void seedPagamentoConfirmado(Long inscricaoId, Long usuarioId, Long eventoId,
                                         Long promotorId, String valorBruto) {
        // Cria via factory com eventoId/promotorId (TECH-S4-01)
        var p = com.ticketeira.payment.domain.Pagamento.pendente(
                inscricaoId, usuarioId, eventoId, promotorId,
                new BigDecimal(valorBruto), new BigDecimal("0.1000"));
        p.confirmar("SIM-" + inscricaoId);
        pagamentoRepository.saveAndFlush(p);
    }

    /** Seed: cria pagamento PENDENTE (nao deve ser repassado). */
    private void seedPagamentoPendente(Long inscricaoId, Long usuarioId, Long eventoId,
                                        Long promotorId) {
        var p = com.ticketeira.payment.domain.Pagamento.pendente(
                inscricaoId, usuarioId, eventoId, promotorId,
                new BigDecimal("100.00"), new BigDecimal("0.1000"));
        pagamentoRepository.saveAndFlush(p);
    }

    /** Publica evento.finalizado com o eventoId dado. */
    private UUID publicarEventoFinalizado(Long eventoId, Long promotorId) {
        UUID eventId = UUID.randomUUID();
        EventoFinalizadoEvent msg = new EventoFinalizadoEvent(
                eventId, eventoId, promotorId, OffsetDateTime.now());
        rabbitTemplate.convertAndSend(EXCHANGE, RK_FINALIZADO, msg);
        return eventId;
    }

    // ---- B2.a ---------------------------------------------------------------

    /**
     * B2.a — eventoFinalizado_repassaConfirmadosDoEvento [US-043]
     * 3 CONFIRMADO do evento 10 (+1 CONFIRMADO de outro evento, +1 PENDENTE do 10)
     * → apos consumo: os 3 do evento 10 → REPASSADO; outros inalterados; 1 processed_event.
     */
    @Test
    @DisplayName("B2.a — evento.finalizado → 3 CONFIRMADO do evento viram REPASSADO")
    void eventoFinalizado_repassaConfirmadosDoEvento() {
        Long eventoId = 10L;
        Long promotorId = 7L;

        // 3 CONFIRMADO do evento 10
        seedPagamentoConfirmado(1L, 101L, eventoId, promotorId, "100.00");
        seedPagamentoConfirmado(2L, 102L, eventoId, promotorId, "100.00");
        seedPagamentoConfirmado(3L, 103L, eventoId, promotorId, "100.00");
        // 1 CONFIRMADO de outro evento (nao deve ser tocado)
        seedPagamentoConfirmado(4L, 104L, 99L, 8L, "100.00");
        // 1 PENDENTE do evento 10 (nao deve ser tocado)
        seedPagamentoPendente(5L, 105L, eventoId, promotorId);

        UUID eventId = publicarEventoFinalizado(eventoId, promotorId);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            // 3 do evento 10 → REPASSADO
            var doEvento = pagamentoRepository.findAll().stream()
                    .filter(p -> eventoId.equals(p.getEventoId()))
                    .toList();
            long repassados = doEvento.stream()
                    .filter(p -> p.getStatus() == StatusPagamento.REPASSADO).count();
            long pendentes = doEvento.stream()
                    .filter(p -> p.getStatus() == StatusPagamento.PENDENTE).count();
            assertThat(repassados).as("3 CONFIRMADO do evento 10 → REPASSADO").isEqualTo(3);
            assertThat(pendentes).as("1 PENDENTE do evento 10 preservado").isEqualTo(1);
            doEvento.stream()
                    .filter(p -> p.getStatus() == StatusPagamento.REPASSADO)
                    .forEach(p -> assertThat(p.getRepassadoEm()).isNotNull());

            // CONFIRMADO de outro evento — inalterado
            var outroEvento = pagamentoRepository.findAll().stream()
                    .filter(p -> Long.valueOf(99L).equals(p.getEventoId()))
                    .findFirst().orElseThrow();
            assertThat(outroEvento.getStatus()).isEqualTo(StatusPagamento.CONFIRMADO);

            // 1 linha em processed_events
            assertThat(processedEventRepository.existsById(eventId)).isTrue();
        });
    }

    // ---- B2.b ---------------------------------------------------------------

    /**
     * B2.b — eventoFinalizado_reentregue2x_repassaApenas1Vez [US-043] CRITICO
     * Mesmo eventId publicado 2x → cada pagamento REPASSADO 1x; 1 linha processed_events.
     */
    @Test
    @DisplayName("B2.b — CRITICO: reentrega do mesmo eventId → repasse aplicado 1x por pagamento")
    void eventoFinalizado_reentregue2x_repassaApenas1Vez() {
        Long eventoId = 20L;
        Long promotorId = 7L;
        seedPagamentoConfirmado(10L, 110L, eventoId, promotorId, "200.00");
        seedPagamentoConfirmado(11L, 111L, eventoId, promotorId, "200.00");

        UUID eventId = UUID.randomUUID();
        EventoFinalizadoEvent msg = new EventoFinalizadoEvent(
                eventId, eventoId, promotorId, OffsetDateTime.now());

        // Primeira entrega
        rabbitTemplate.convertAndSend(EXCHANGE, RK_FINALIZADO, msg);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            long repassados = pagamentoRepository.findAll().stream()
                    .filter(p -> p.getStatus() == StatusPagamento.REPASSADO).count();
            assertThat(repassados).isEqualTo(2);
        });

        // Segunda entrega (mesma mensagem — reentrega at-least-once)
        rabbitTemplate.convertAndSend(EXCHANGE, RK_FINALIZADO, msg);

        await().during(3, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // CRITICO: ainda 2 REPASSADO (nao re-transicionou)
            long repassados = pagamentoRepository.findAll().stream()
                    .filter(p -> p.getStatus() == StatusPagamento.REPASSADO).count();
            assertThat(repassados).as("Reentrega nao deve re-transicionar").isEqualTo(2);
            // 1 linha em processed_events (PK impede duplicata)
            assertThat(processedEventRepository.count())
                    .as("Apenas 1 processed_event mesmo apos reentrega").isEqualTo(1);
        });
    }

    // ---- B2.c ---------------------------------------------------------------

    /**
     * B2.c — eventoFinalizado_pagamentoNaoConfirmado_naoToca [US-043 crit.4]
     * Pagamento ja REEMBOLSADO do evento → evento.finalizado NAO o altera.
     */
    @Test
    @DisplayName("B2.c — pagamento REEMBOLSADO do evento nao e tocado pelo evento.finalizado")
    void eventoFinalizado_pagamentoNaoConfirmado_naoToca() {
        Long eventoId = 30L;
        Long promotorId = 7L;

        // Cria CONFIRMADO e depois "reembolsa" manualmente para simular estado REEMBOLSADO
        var p = com.ticketeira.payment.domain.Pagamento.pendente(
                20L, 120L, eventoId, promotorId,
                new BigDecimal("100.00"), new BigDecimal("0.1000"));
        p.confirmar("SIM-20");
        p.reembolsar();
        pagamentoRepository.saveAndFlush(p);

        UUID eventId = publicarEventoFinalizado(eventoId, promotorId);

        await().during(3, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var pagamento = pagamentoRepository.findAll().stream()
                    .filter(pg -> eventoId.equals(pg.getEventoId()))
                    .findFirst().orElseThrow();
            assertThat(pagamento.getStatus())
                    .as("Pagamento REEMBOLSADO nao deve virar REPASSADO")
                    .isEqualTo(StatusPagamento.REEMBOLSADO);
            // processed_events gravado (consumidor fez ACK normal)
            assertThat(processedEventRepository.existsById(eventId)).isTrue();
        });
    }

    // ---- B2.d ---------------------------------------------------------------

    /**
     * B2.d — eventoFinalizado_semPagamentos_ackNoop [US-043]
     * Evento sem pagamentos CONFIRMADO → consumidor faz no-op, ACK, processed_event gravado.
     */
    @Test
    @DisplayName("B2.d — evento sem pagamentos CONFIRMADO → ACK no-op, sem DLQ")
    void eventoFinalizado_semPagamentos_ackNoop() {
        Long eventoId = 40L;

        UUID eventId = publicarEventoFinalizado(eventoId, 7L);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(processedEventRepository.existsById(eventId))
                        .as("processed_event gravado mesmo sem pagamentos")
                        .isTrue());

        // Nenhum pagamento foi criado ou alterado
        assertThat(pagamentoRepository.count()).isZero();
    }

    // ---- B2.e ---------------------------------------------------------------

    /**
     * B2.e — valorRepasse_jaComputadoNoS4_naoRecalcula [US-043]
     * valor_repasse (bruto-taxa) permanece do S4; repasse so muda status/repassadoEm.
     */
    @Test
    @DisplayName("B2.e — valorRepasse existente preservado; repasse so muda status/repassadoEm")
    void valorRepasse_jaComputadoNoS4_naoRecalcula() {
        Long eventoId = 50L;
        Long promotorId = 7L;
        seedPagamentoConfirmado(30L, 130L, eventoId, promotorId, "100.00");

        publicarEventoFinalizado(eventoId, promotorId);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var p = pagamentoRepository.findAll().stream()
                    .filter(pg -> eventoId.equals(pg.getEventoId()))
                    .findFirst().orElseThrow();
            assertThat(p.getStatus()).isEqualTo(StatusPagamento.REPASSADO);
            // valor_repasse permanece 90.00 (100 - 10%)
            assertThat(p.getValorRepasse())
                    .as("valorRepasse = 90.00 (computado no S4, nao recalculado)")
                    .isEqualByComparingTo(new BigDecimal("90.00"));
            assertThat(p.getRepassadoEm()).isNotNull();
        });
    }
}
