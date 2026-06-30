package com.ticketeira.payment.messaging;

import com.ticketeira.payment.TestcontainersBase;
import com.ticketeira.payment.domain.StatusPagamento;
import com.ticketeira.payment.repository.PagamentoRepository;
import com.ticketeira.payment.repository.ProcessedEventRepository;
import com.ticketeira.payment.repository.ReembolsoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
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
 * C2 — Corrida repasse-vs-reembolso no mesmo pagamento [US-042/US-043] CRITICO
 * Caso: C2 (tests-spec.md, §7 concorrencia).
 *
 * 1 pagamento CONFIRMADO; dispara evento.finalizado{10} e evento.cancelado{10}
 * concorrentemente → o pagamento termina em EXATAMENTE um de {REPASSADO, REEMBOLSADO},
 * NUNCA os dois. @RepeatedTest(3) para cobrir variacao de scheduling.
 *
 * VERMELHO: EventoFinalizadoListener, EventoCanceladoListener nao existem.
 */
@Tag("integracao")
@SpringBootTest
@ActiveProfiles("test-postgres")
@DisplayName("C2 — Corrida repasse-vs-reembolso no mesmo pagamento (row lock)")
class CorridaRepasseReembolsoTest extends TestcontainersBase {

    private static final String EXCHANGE         = "ticketeira.events";
    private static final String RK_FINALIZADO    = "evento.finalizado";
    private static final String RK_CANCELADO     = "evento.cancelado";
    private static final String QUEUE_FINALIZADO  = "evento.finalizado";
    private static final String QUEUE_CANCELADO   = "evento.cancelado";

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
        // Aprendizado S4: purgar filas para isolamento entre repeticoes.
        rabbitAdmin.purgeQueue(QUEUE_FINALIZADO, false);
        rabbitAdmin.purgeQueue(QUEUE_CANCELADO, false);
    }

    /**
     * C2 — corrida_repasseEReembolso_mesmoPagamento_apenasUmVence [US-042/US-043] CRITICO
     *
     * Cenario: 1 pagamento CONFIRMADO do eventoId=10.
     * Disparo concorrente de evento.finalizado{eventId=X, eventoId=10}
     *                    e evento.cancelado {eventId=Y, eventoId=10}.
     * (eventIds distintos — sao duas sagas distintas.)
     *
     * Invariante: o pagamento termina em EXATAMENTE um de {REPASSADO, REEMBOLSADO}.
     *   - Se REPASSADO venceu: 0 reembolsos criados.
     *   - Se REEMBOLSADO venceu: 0 repasse (status nao casou com CONFIRMADO na tx do finalizado).
     * O row lock do Postgres (UPDATE...WHERE status='CONFIRMADO') serializa as duas tx.
     *
     * @RepeatedTest(3) cobre variacao de scheduling entre as entregas.
     */
    @RepeatedTest(3)
    @DisplayName("C2 — CRITICO: repasse vs reembolso no mesmo pagamento → exatamente 1 vence")
    void corrida_repasseEReembolso_mesmoPagamento_apenasUmVence() {
        Long eventoId = 10L;
        Long promotorId = 7L;

        // 1 pagamento CONFIRMADO
        var p = com.ticketeira.payment.domain.Pagamento.pendente(
                1L, 101L, eventoId, promotorId,
                new BigDecimal("100.00"), new BigDecimal("0.1000"));
        p.confirmar("SIM-1");
        pagamentoRepository.saveAndFlush(p);

        // EventIds distintos (duas sagas independentes)
        UUID eventIdFinalizado = UUID.randomUUID();
        UUID eventIdCancelado  = UUID.randomUUID();

        EventoFinalizadoEvent msgFinalizado = new EventoFinalizadoEvent(
                eventIdFinalizado, eventoId, promotorId, OffsetDateTime.now());
        EventoCanceladoEvent msgCancelado = new EventoCanceladoEvent(
                eventIdCancelado, eventoId, promotorId, OffsetDateTime.now());

        // Disparo concorrente: publica os dois quase ao mesmo tempo
        rabbitTemplate.convertAndSend(EXCHANGE, RK_FINALIZADO, msgFinalizado);
        rabbitTemplate.convertAndSend(EXCHANGE, RK_CANCELADO, msgCancelado);

        // Aguarda ambos os processed_events (ambos os consumidores processaram)
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            // Ambas as mensagens foram processadas (ACK — incluindo o no-op)
            assertThat(processedEventRepository.existsById(eventIdFinalizado)
                    || processedEventRepository.existsById(eventIdCancelado))
                    .as("Pelo menos um processed_event deve existir").isTrue();

            var pagamento = pagamentoRepository.findAll().stream().findFirst().orElseThrow();

            // INVARIANTE: o pagamento esta em exatamente um dos dois estados finais
            assertThat(pagamento.getStatus())
                    .as("Pagamento deve ser REPASSADO ou REEMBOLSADO — nunca CONFIRMADO")
                    .isIn(StatusPagamento.REPASSADO, StatusPagamento.REEMBOLSADO);

            if (pagamento.getStatus() == StatusPagamento.REPASSADO) {
                // Repasse venceu: 0 reembolsos
                assertThat(reembolsoRepository.count())
                        .as("REPASSADO venceu → 0 reembolsos").isZero();
                assertThat(pagamento.getRepassadoEm()).isNotNull();
                assertThat(pagamento.getReembolsadoEm()).isNull();
            } else {
                // Reembolso venceu: 1 reembolso
                assertThat(reembolsoRepository.count())
                        .as("REEMBOLSADO venceu → 1 reembolso").isEqualTo(1);
                assertThat(pagamento.getReembolsadoEm()).isNotNull();
                assertThat(pagamento.getRepassadoEm()).isNull();
            }
        });
    }
}
