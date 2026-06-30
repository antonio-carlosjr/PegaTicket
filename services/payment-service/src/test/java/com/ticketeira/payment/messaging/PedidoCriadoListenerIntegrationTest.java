package com.ticketeira.payment.messaging;

import com.ticketeira.payment.TestcontainersBase;
import com.ticketeira.payment.domain.StatusPagamento;
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
 * Testes de integracao do consumidor pedido.criado (B1).
 * Requer Postgres + RabbitMQ reais (Testcontainers).
 * Verifica:
 * - B1.a: pedidoCriado_criaPagamentoPendente_comEscrowComputado
 * - B1.b: pedidoCriado_reentregue2x_criaApenas1Pagamento (CRITICO — idempotencia)
 * Casos de teste: B1 (tests-spec.md)
 */
@Tag("integracao")
@SpringBootTest
@ActiveProfiles("test-postgres")
@DisplayName("B1 — Consumidor pedido.criado")
class PedidoCriadoListenerIntegrationTest extends TestcontainersBase {

    private static final String EXCHANGE = "ticketeira.events";
    private static final String ROUTING_KEY = "pedido.criado";

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    PagamentoRepository pagamentoRepository;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @BeforeEach
    void limpar() {
        pagamentoRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    @Test
    @DisplayName("B1.a — pedidoCriado_criaPagamentoPendente_comEscrowComputado")
    void pedidoCriado_criaPagamentoPendente_comEscrowComputado() {
        UUID eventId = UUID.randomUUID();
        PedidoCriadoEvent evento = new PedidoCriadoEvent(
                eventId,
                42L,        // inscricaoId
                10L,        // usuarioId
                100L,       // eventoId
                new BigDecimal("100.00"),  // valor bruto
                5L,         // promotorId
                OffsetDateTime.now()
        );

        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, evento);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(pagamentoRepository.count()).isEqualTo(1);
        });

        var pagamento = pagamentoRepository.findByInscricaoId(42L).orElseThrow();
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(pagamento.getValorBruto()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(pagamento.getValorTaxa()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(pagamento.getValorRepasse()).isEqualByComparingTo(new BigDecimal("90.00"));
        assertThat(pagamento.getUsuarioId()).isEqualTo(10L);
        assertThat(pagamento.getGatewayPaymentId()).isNull();

        // 1 registro em processed_events
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }

    @Test
    @DisplayName("B1.b — pedidoCriado_reentregue2x_criaApenas1Pagamento (CRITICO)")
    void pedidoCriado_reentregue2x_criaApenas1Pagamento() {
        UUID eventId = UUID.randomUUID(); // mesmo eventId nas 2 entregas
        PedidoCriadoEvent evento = new PedidoCriadoEvent(
                eventId,
                99L,        // inscricaoId
                20L,        // usuarioId
                200L,       // eventoId
                new BigDecimal("99.99"),
                7L,
                OffsetDateTime.now()
        );

        // Primeira entrega
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, evento);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(pagamentoRepository.findByInscricaoId(99L)).isPresent();
        });

        // Segunda entrega (reentrega — mesmo eventId, cenario at-least-once)
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, evento);

        // Aguarda um tempo para garantir que o consumidor processou ambas
        await().during(2, TimeUnit.SECONDS).atMost(8, TimeUnit.SECONDS).untilAsserted(() -> {
            // CRITICO: deve haver EXATAMENTE 1 pagamento e 1 processed_event
            assertThat(pagamentoRepository.count()).isEqualTo(1);
            assertThat(processedEventRepository.count()).isEqualTo(1);
        });
    }
}
