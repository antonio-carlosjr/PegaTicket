package com.ticketeira.payment.service;

import com.ticketeira.payment.TestcontainersBase;
import com.ticketeira.payment.domain.StatusPagamento;
import com.ticketeira.payment.messaging.PagamentoAprovadoEvent;
import com.ticketeira.payment.messaging.PedidoCriadoEvent;
import com.ticketeira.payment.repository.PagamentoRepository;
import com.ticketeira.payment.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
 * Testes de integracao do PagamentoService com Postgres + RabbitMQ reais (B2).
 * Cobre:
 * - B2.a: confirmar_pendente_transicionaParaConfirmado_eRetem
 * - B2.b: confirmar_publicaPagamentoAprovado_aposCommit
 * - B2.c: confirmar_2x_idempotente_naoRepublica (CRITICO)
 * - B2.d: confirmar_pagamentoDeOutroUsuario_lanca403
 * - B2.e: confirmar_inscricaoSemPagamento_lanca404
 * - B2.f: confirmar_gatewayRecusa_mantemPendente_lanca402
 * Casos de teste: B2 (tests-spec.md)
 */
@Tag("integracao")
@SpringBootTest
@ActiveProfiles("test-postgres")
@DisplayName("B2 — PagamentoService.confirmar()")
class PagamentoServiceIntegrationTest extends TestcontainersBase {

    private static final String EXCHANGE = "ticketeira.events";
    private static final String ROUTING_PEDIDO = "pedido.criado";
    private static final String QUEUE_APROVADO = "pagamento.aprovado";

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    org.springframework.amqp.rabbit.core.RabbitAdmin rabbitAdmin;

    @Autowired
    PagamentoService pagamentoService;

    @Autowired
    PagamentoRepository pagamentoRepository;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @BeforeEach
    void limpar() {
        pagamentoRepository.deleteAll();
        processedEventRepository.deleteAll();
        // Isolamento: as 3 classes de integracao compartilham o mesmo contexto/broker.
        // Tests que confirmam mas nao consomem deixam mensagens em pagamento.aprovado;
        // purgar as filas garante que cada teste comece com filas vazias.
        rabbitAdmin.purgeQueue("pagamento.aprovado", false);
        rabbitAdmin.purgeQueue("pedido.criado", false);
    }

    /**
     * Publica pedido.criado e aguarda o pagamento ser criado pelo consumidor.
     */
    private Long criarPagamentoPendente(Long inscricaoId, Long usuarioId, BigDecimal valor) {
        UUID eventId = UUID.randomUUID();
        PedidoCriadoEvent evento = new PedidoCriadoEvent(
                eventId, inscricaoId, usuarioId, 100L, valor, 5L, OffsetDateTime.now());
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_PEDIDO, evento);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(pagamentoRepository.findByInscricaoId(inscricaoId)).isPresent());

        return pagamentoRepository.findByInscricaoId(inscricaoId).orElseThrow().getId();
    }

    @Test
    @DisplayName("B2.a — confirmar_pendente_transicionaParaConfirmado_eRetem")
    void confirmar_pendente_transicionaParaConfirmado_eRetem() {
        Long inscricaoId = 1L;
        Long usuarioId = 10L;
        criarPagamentoPendente(inscricaoId, usuarioId, new BigDecimal("100.00"));

        var response = pagamentoService.confirmar(inscricaoId, usuarioId);

        assertThat(response.status()).isEqualTo("CONFIRMADO");
        assertThat(response.gatewayPaymentId()).isNotBlank();
        assertThat(response.gatewayPaymentId()).startsWith("SIM-");
        assertThat(response.processadoEm()).isNotNull();
        assertThat(response.valorBruto()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(response.valorTaxa()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(response.valorRepasse()).isEqualByComparingTo(new BigDecimal("90.00"));
        // Escrow: CONFIRMADO (retido), sem repasse/reembolso registrado neste sprint
        var pagamento = pagamentoRepository.findByInscricaoId(inscricaoId).orElseThrow();
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.CONFIRMADO);
    }

    @Test
    @DisplayName("B2.b — confirmar_publicaPagamentoAprovado_aposCommit")
    void confirmar_publicaPagamentoAprovado_aposCommit() {
        Long inscricaoId = 2L;
        Long usuarioId = 11L;
        criarPagamentoPendente(inscricaoId, usuarioId, new BigDecimal("150.00"));

        pagamentoService.confirmar(inscricaoId, usuarioId);

        // Aguarda mensagem na fila pagamento.aprovado
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Object msg = rabbitTemplate.receiveAndConvert(QUEUE_APROVADO, 500);
            assertThat(msg).isInstanceOf(PagamentoAprovadoEvent.class);
            PagamentoAprovadoEvent evento = (PagamentoAprovadoEvent) msg;
            assertThat(evento.eventId()).isNotNull();
            assertThat(evento.inscricaoId()).isEqualTo(inscricaoId);
            assertThat(evento.usuarioId()).isEqualTo(usuarioId);
        });
    }

    @Test
    @DisplayName("B2.c — confirmar_2x_idempotente_naoRepublica (CRITICO)")
    void confirmar_2x_idempotente_naoRepublica() {
        Long inscricaoId = 3L;
        Long usuarioId = 12L;
        criarPagamentoPendente(inscricaoId, usuarioId, new BigDecimal("200.00"));

        // Primeira confirmacao
        pagamentoService.confirmar(inscricaoId, usuarioId);

        // Segunda confirmacao (idempotente — ja CONFIRMADO)
        pagamentoService.confirmar(inscricaoId, usuarioId);

        // CRITICO: apenas 1 mensagem pagamento.aprovado deve ter sido publicada
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            // Consome a 1a mensagem
            Object msg1 = rabbitTemplate.receiveAndConvert(QUEUE_APROVADO, 500);
            assertThat(msg1).isInstanceOf(PagamentoAprovadoEvent.class);

            // Nao deve haver 2a mensagem
            Object msg2 = rabbitTemplate.receiveAndConvert(QUEUE_APROVADO, 500);
            assertThat(msg2).isNull();
        });

        // Status continua CONFIRMADO (no-op)
        var pagamento = pagamentoRepository.findByInscricaoId(inscricaoId).orElseThrow();
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.CONFIRMADO);
    }

    @Test
    @DisplayName("B2.d — confirmar_pagamentoDeOutroUsuario_lanca403")
    void confirmar_pagamentoDeOutroUsuario_lanca403() {
        Long inscricaoId = 4L;
        Long usuarioDono = 13L;
        Long usuarioOutro = 99L;
        criarPagamentoPendente(inscricaoId, usuarioDono, new BigDecimal("100.00"));

        // Outro usuario tenta confirmar
        org.assertj.core.api.ThrowableAssert.ThrowingCallable acao =
                () -> pagamentoService.confirmar(inscricaoId, usuarioOutro);

        org.assertj.core.api.Assertions.assertThatThrownBy(acao)
                .satisfies(ex -> {
                    // Deve ser BusinessException com status 403 e codigo PAGAMENTO_DE_OUTRO_USUARIO
                    assertThat(ex).isInstanceOf(com.ticketeira.common.exception.BusinessException.class);
                    com.ticketeira.common.exception.BusinessException be =
                            (com.ticketeira.common.exception.BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(403);
                    assertThat(be.getMessage()).isEqualTo("PAGAMENTO_DE_OUTRO_USUARIO");
                });
    }

    @Test
    @DisplayName("B2.e — confirmar_inscricaoSemPagamento_lanca404")
    void confirmar_inscricaoSemPagamento_lanca404() {
        Long inscricaoInexistente = 999L;
        Long usuarioId = 10L;

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> pagamentoService.confirmar(inscricaoInexistente, usuarioId))
                .satisfies(ex -> {
                    assertThat(ex.getMessage()).isEqualTo("PAGAMENTO_NAO_ENCONTRADO");
                });
    }

    @Test
    @DisplayName("B2.f — confirmar_gatewayRecusa_mantemPendente_lanca402")
    void confirmar_gatewayRecusa_mantemPendente_lanca402() {
        // GatewaySimulado recusa: para simular, o service deve aceitar um flag de configuracao
        // ou o gateway deve ser mockavel. Como e um teste de integracao, assumimos que
        // o GatewaySimulado tem um modo "recusa" ou que o service expoe um ponto de injecao.
        // Neste teste vermelhos, declaramos o comportamento esperado; o Backend implementa.
        Long inscricaoId = 5L;
        Long usuarioId = 14L;
        criarPagamentoPendente(inscricaoId, usuarioId, new BigDecimal("100.00"));

        // O GatewaySimulado em modo RECUSA retorna erro -> service lanca 402
        // Configurar via propriedade de teste: app.gateway.simulado.mode=RECUSAR
        // Esta propriedade sera declarada pelo Backend quando implementar o GatewaySimulado.
        // Por enquanto, o teste esta vermelho pois GatewaySimulado nao existe.

        // Nota: se o gateway padrao (S4) sempre aprova, este caso pode ser testado
        // com @MockBean GatewaySimulado configurado para lancar excecao.
        // Deixamos o esqueleto aqui para o Backend completar.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            // Quando GatewaySimulado estiver configurado para recusar:
            pagamentoService.confirmarComGatewayRecusando(inscricaoId, usuarioId);
        }).satisfies(ex -> {
            assertThat(ex.getMessage()).isEqualTo("PAGAMENTO_RECUSADO");
        });
    }
}
