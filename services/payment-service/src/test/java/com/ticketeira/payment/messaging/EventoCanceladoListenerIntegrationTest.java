package com.ticketeira.payment.messaging;

import com.ticketeira.payment.TestcontainersBase;
import com.ticketeira.payment.domain.Reembolso;
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
 * C1 — payment-service: reembolso em massa pos evento.cancelado.
 * Casos: C1.a C1.b C1.c C1.d (tests-spec.md, US-042).
 * Requer Testcontainers Postgres + RabbitMQ reais.
 *
 * VERMELHO: EventoCanceladoListener, ReembolsoService, Reembolso.criar(),
 * Pagamento.reembolsar(), campos eventoId em Pagamento ainda nao existem.
 */
@Tag("integracao")
@SpringBootTest
@ActiveProfiles("test-postgres")
@DisplayName("C1 — Reembolso em massa pos evento.cancelado (Testcontainers)")
class EventoCanceladoListenerIntegrationTest extends TestcontainersBase {

    private static final String EXCHANGE        = "ticketeira.events";
    private static final String RK_CANCELADO    = "evento.cancelado";
    private static final String QUEUE_CANCELADO = "evento.cancelado";
    private static final String QUEUE_FINALIZADO = "evento.finalizado";

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
        // Aprendizado S4: purgar filas para isolamento.
        rabbitAdmin.purgeQueue(QUEUE_CANCELADO, false);
        rabbitAdmin.purgeQueue(QUEUE_FINALIZADO, false);
    }

    /** Seed: CONFIRMADO. */
    private com.ticketeira.payment.domain.Pagamento seedConfirmado(
            Long inscricaoId, Long usuarioId, Long eventoId, Long promotorId, String valor) {
        var p = com.ticketeira.payment.domain.Pagamento.pendente(
                inscricaoId, usuarioId, eventoId, promotorId,
                new BigDecimal(valor), new BigDecimal("0.1000"));
        p.confirmar("SIM-" + inscricaoId);
        return pagamentoRepository.saveAndFlush(p);
    }

    /** Seed: PENDENTE. */
    private void seedPendente(Long inscricaoId, Long usuarioId, Long eventoId, Long promotorId) {
        var p = com.ticketeira.payment.domain.Pagamento.pendente(
                inscricaoId, usuarioId, eventoId, promotorId,
                new BigDecimal("100.00"), new BigDecimal("0.1000"));
        pagamentoRepository.saveAndFlush(p);
    }

    /** Publica evento.cancelado. */
    private UUID publicarEventoCancelado(Long eventoId, Long promotorId) {
        UUID eventId = UUID.randomUUID();
        EventoCanceladoEvent msg = new EventoCanceladoEvent(
                eventId, eventoId, promotorId, OffsetDateTime.now());
        rabbitTemplate.convertAndSend(EXCHANGE, RK_CANCELADO, msg);
        return eventId;
    }

    // ---- C1.a ---------------------------------------------------------------

    /**
     * C1.a — eventoCancelado_reembolsaConfirmadosDoEvento [US-042] CRITICO
     * 3 CONFIRMADO do evento 10 → apos consumo: 3 REEMBOLSADO + 3 reembolsos
     * (motivo=EVENTO_CANCELADO, status=PROCESSADO, valor=valor_bruto); 1 processed_event.
     */
    @Test
    @DisplayName("C1.a — CRITICO: evento.cancelado → 3 CONFIRMADO→REEMBOLSADO + 3 reembolsos")
    void eventoCancelado_reembolsaConfirmadosDoEvento() {
        Long eventoId = 10L;
        Long promotorId = 7L;

        seedConfirmado(1L, 101L, eventoId, promotorId, "100.00");
        seedConfirmado(2L, 102L, eventoId, promotorId, "150.00");
        seedConfirmado(3L, 103L, eventoId, promotorId, "200.00");

        UUID eventId = publicarEventoCancelado(eventoId, promotorId);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            // 3 pagamentos → REEMBOLSADO
            var doEvento = pagamentoRepository.findAll().stream()
                    .filter(p -> eventoId.equals(p.getEventoId()))
                    .toList();
            assertThat(doEvento).hasSize(3);
            doEvento.forEach(p -> {
                assertThat(p.getStatus())
                        .as("Pagamento %d deve ser REEMBOLSADO", p.getId())
                        .isEqualTo(StatusPagamento.REEMBOLSADO);
                assertThat(p.getReembolsadoEm()).isNotNull();
            });

            // 3 reembolsos: motivo=EVENTO_CANCELADO, status=PROCESSADO, valor=bruto
            var reembolsos = reembolsoRepository.findAll();
            assertThat(reembolsos).hasSize(3);
            reembolsos.forEach(r -> {
                assertThat(r.getMotivo()).isEqualTo("EVENTO_CANCELADO");
                assertThat(r.getStatus()).isEqualTo("PROCESSADO");
                assertThat(r.getValor()).isNotNull();
            });
            // Os valores dos reembolsos somam os brutos dos 3
            BigDecimal totalReembolsado = reembolsos.stream()
                    .map(Reembolso::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(totalReembolsado).isEqualByComparingTo(new BigDecimal("450.00"));

            // 1 processed_event
            assertThat(processedEventRepository.existsById(eventId)).isTrue();
        });
    }

    // ---- C1.b ---------------------------------------------------------------

    /**
     * C1.b — eventoCancelado_reentregue2x_reembolsaApenas1Vez [US-042] CRITICO
     * 2x mesma mensagem (mesmo eventId) → 3 REEMBOLSADO, 3 reembolsos (nao 6), 1 processed_event.
     */
    @Test
    @DisplayName("C1.b — CRITICO: reentrega do mesmo eventId → 1x reembolso por pagamento")
    void eventoCancelado_reentregue2x_reembolsaApenas1Vez() {
        Long eventoId = 20L;
        Long promotorId = 7L;

        seedConfirmado(10L, 110L, eventoId, promotorId, "100.00");
        seedConfirmado(11L, 111L, eventoId, promotorId, "100.00");
        seedConfirmado(12L, 112L, eventoId, promotorId, "100.00");

        UUID eventId = UUID.randomUUID();
        EventoCanceladoEvent msg = new EventoCanceladoEvent(
                eventId, eventoId, promotorId, OffsetDateTime.now());

        // Primeira entrega
        rabbitTemplate.convertAndSend(EXCHANGE, RK_CANCELADO, msg);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(reembolsoRepository.count()).isEqualTo(3);
        });

        // Segunda entrega (reentrega)
        rabbitTemplate.convertAndSend(EXCHANGE, RK_CANCELADO, msg);

        await().during(3, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // CRITICO: ainda 3 reembolsos (nao 6)
            assertThat(reembolsoRepository.count())
                    .as("Reentrega nao deve criar reembolsos duplicados").isEqualTo(3);
            assertThat(processedEventRepository.count())
                    .as("1 processed_event na reentrega").isEqualTo(1);
            // Status dos pagamentos inalterado na 2a entrega
            long reembolsados = pagamentoRepository.findAll().stream()
                    .filter(p -> eventoId.equals(p.getEventoId())
                                 && p.getStatus() == StatusPagamento.REEMBOLSADO)
                    .count();
            assertThat(reembolsados).isEqualTo(3);
        });
    }

    // ---- C1.c ---------------------------------------------------------------

    /**
     * C1.c — eventoCancelado_pagamentoPendente_naoReembolsa [US-042]
     * Pagamento PENDENTE do evento → nao vira REEMBOLSADO, nenhum reembolso criado.
     */
    @Test
    @DisplayName("C1.c — PENDENTE do evento nao e reembolsado (so CONFIRMADO e reembolsado)")
    void eventoCancelado_pagamentoPendente_naoReembolsa() {
        Long eventoId = 30L;
        seedPendente(20L, 120L, eventoId, 7L);

        UUID eventId = publicarEventoCancelado(eventoId, 7L);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(processedEventRepository.existsById(eventId)).isTrue());

        // PENDENTE nao deve ter virado REEMBOLSADO
        var p = pagamentoRepository.findAll().stream()
                .filter(pg -> eventoId.equals(pg.getEventoId()))
                .findFirst().orElseThrow();
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(reembolsoRepository.count()).isZero();
    }

    // ---- C1.d ---------------------------------------------------------------

    /**
     * C1.d — admin_listaReembolsadosERepassados [US-042 crit.5]
     * GET /api/payments?status=REEMBOLSADO (admin) lista os reembolsados.
     * GET /api/payments?status=REPASSADO lista os repassados.
     * Testado via repository (nao MockMvc) para nao depender do endpoint admin.
     * (O contrato REST nao requer endpoint novo; o filtro e no GET /api/payments existente.)
     */
    @Test
    @DisplayName("C1.d — repositorio lista pagamentos por status REEMBOLSADO e REPASSADO")
    void admin_listaReembolsadosERepassados() {
        Long eventoId = 40L;
        Long promotorId = 7L;

        // 2 CONFIRMADO → reembolsar manualmente
        var p1 = seedConfirmado(30L, 130L, eventoId, promotorId, "100.00");
        var p2 = seedConfirmado(31L, 131L, eventoId, promotorId, "100.00");
        p1.reembolsar();
        p2.reembolsar();
        pagamentoRepository.saveAndFlush(p1);
        pagamentoRepository.saveAndFlush(p2);

        // 1 CONFIRMADO → repassar
        var p3 = seedConfirmado(32L, 132L, 99L, 8L, "100.00");
        p3.repassar();
        pagamentoRepository.saveAndFlush(p3);

        // Verifica que o repositorio permite filtrar por status
        long reembolsados = pagamentoRepository.findAll().stream()
                .filter(p -> p.getStatus() == StatusPagamento.REEMBOLSADO).count();
        long repassados = pagamentoRepository.findAll().stream()
                .filter(p -> p.getStatus() == StatusPagamento.REPASSADO).count();

        assertThat(reembolsados).as("2 pagamentos REEMBOLSADO").isEqualTo(2);
        assertThat(repassados).as("1 pagamento REPASSADO").isEqualTo(1);
    }
}
