package com.ticketeira.payment.messaging;

import com.ticketeira.payment.TestcontainersBase;
import com.ticketeira.payment.domain.StatusPagamento;
import com.ticketeira.payment.repository.PagamentoRepository;
import com.ticketeira.payment.repository.ProcessedEventRepository;
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
 * B1 — TECH-S4-01: criarPendente persiste eventoId/promotorId.
 * Casos: B1.a B1.b (tests-spec.md, TECH-S4-01).
 *
 * VERMELHO: Pagamento.pendente(inscricaoId, usuarioId, eventoId, promotorId, valor, taxa)
 * nao existe (assinatura antiga nao tem eventoId/promotorId);
 * PagamentoResponse nao expoe eventoId/promotorId.
 */
@Tag("integracao")
@SpringBootTest
@ActiveProfiles("test-postgres")
@DisplayName("B1 — TECH-S4-01: pedido.criado persiste eventoId e promotorId")
class PedidoCriadoEventoIdIntegrationTest extends TestcontainersBase {

    private static final String EXCHANGE    = "ticketeira.events";
    private static final String RK_PEDIDO   = "pedido.criado";
    private static final String QUEUE_PEDIDO = "pedido.criado";

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
        // Aprendizado S4: purgar filas para isolamento.
        rabbitAdmin.purgeQueue(QUEUE_PEDIDO, false);
    }

    // ---- B1.a ---------------------------------------------------------------

    /**
     * B1.a — criarPendente_persisteEventoIdEPromotorId [TECH-S4-01]
     * Consumir pedido.criado{eventoId=10, promotorId=7, valor=100}
     * → Pagamento salvo com eventoId=10, promotorId=7, valorBruto=100.00.
     */
    @Test
    @DisplayName("B1.a — pedido.criado → Pagamento persiste eventoId e promotorId")
    void criarPendente_persisteEventoIdEPromotorId() {
        UUID eventId = UUID.randomUUID();
        Long eventoId = 10L;
        Long promotorId = 7L;

        // PedidoCriadoEvent ja carrega eventoId/promotorId desde o S4
        PedidoCriadoEvent pedido = new PedidoCriadoEvent(
                eventId,
                50L,         // inscricaoId
                200L,        // usuarioId
                eventoId,    // eventoId
                new BigDecimal("100.00"),
                promotorId,  // promotorId
                OffsetDateTime.now()
        );

        rabbitTemplate.convertAndSend(EXCHANGE, RK_PEDIDO, pedido);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(pagamentoRepository.count()).isEqualTo(1);
        });

        var pagamento = pagamentoRepository.findAll().get(0);
        assertThat(pagamento.getEventoId())
                .as("B1.a — eventoId deve ser persistido").isEqualTo(eventoId);
        assertThat(pagamento.getPromotorId())
                .as("B1.a — promotorId deve ser persistido").isEqualTo(promotorId);
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(pagamento.getValorBruto()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(pagamento.getValorTaxa()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(pagamento.getValorRepasse()).isEqualByComparingTo(new BigDecimal("90.00"));

        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }

    // ---- B1.b (controller) --------------------------------------------------

    /**
     * B1.b — pagamentoResponse_incluiEventoIdEPromotorId [TECH-S4-01]
     * GET /payments/me retorna eventoId e promotorId no JSON.
     * (Testado via repositorio — o contrato REST e validado pelo controller test.)
     * VERMELHO: PagamentoResponse nao tem eventoId/promotorId.
     */
    @Test
    @DisplayName("B1.b — PagamentoResponse expoe eventoId e promotorId (campos TECH-S4-01)")
    void pagamentoResponse_incluiEventoIdEPromotorId() {
        Long eventoId = 15L;
        Long promotorId = 9L;

        var p = com.ticketeira.payment.domain.Pagamento.pendente(
                60L, 210L, eventoId, promotorId,
                new BigDecimal("200.00"), new BigDecimal("0.1000"));
        pagamentoRepository.saveAndFlush(p);

        var salvo = pagamentoRepository.findAll().get(0);
        // Validar que os campos foram gravados no banco (precondition para o response)
        assertThat(salvo.getEventoId()).isEqualTo(eventoId);
        assertThat(salvo.getPromotorId()).isEqualTo(promotorId);

        // A validacao do response JSON fica no PaymentControllerIntegrationTest (endpoint GET /payments/me).
        // Aqui provamos que o modelo de dados suporta os campos (TDD: compile/estrutura).
        // VERMELHO: getEventoId()/getPromotorId() nao existem na entidade Pagamento atual.
    }
}
