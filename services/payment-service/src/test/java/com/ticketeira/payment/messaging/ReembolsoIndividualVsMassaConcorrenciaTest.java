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
 * C2 — Corrida rara: reembolso individual vs. reembolso em massa no mesmo pagamento. [US-035 + US-042] CRITICO
 * Casos: C2.a
 *
 * Mesmo pagamento CONFIRMADO recebe inscricao.cancelada (individual) E evento.cancelado (massa) simultaneos.
 * Invariante: exatamente 1 reembolso aplicado (transicao condicional + lock pessimista).
 *
 * VERMELHO: InscricaoCanceladaListener nao existe.
 */
@Tag("concorrencia")
@SpringBootTest
@ActiveProfiles("test-postgres")
@DisplayName("C2 — Corrida individual-vs-massa (CRITICO — Testcontainers PG+Rabbit)")
class ReembolsoIndividualVsMassaConcorrenciaTest extends TestcontainersBase {

    private static final String EXCHANGE                 = "ticketeira.events";
    private static final String RK_INSCRICAO_CANCELADA   = "inscricao.cancelada";
    private static final String RK_EVENTO_CANCELADO      = "evento.cancelado";
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
        rabbitAdmin.purgeQueue(QUEUE_INSCRICAO_CANCELADA, false);
        rabbitAdmin.purgeQueue(QUEUE_CANCELADO, false);
        rabbitAdmin.purgeQueue(QUEUE_FINALIZADO, false);
    }

    // C2.a -------------------------------------------------------------------

    /**
     * C2.a — mesmo pagamento CONFIRMADO recebe inscricao.cancelada E evento.cancelado quase juntos ->
     * exatamente 1 reembolso aplicado; pagamento REEMBOLSADO 1x; nunca 2 reembolsos. [US-035 / corrida]
     *
     * Aceita-se que motivos podem diferir (CANCELAMENTO_PARTICIPANTE vs EVENTO_CANCELADO);
     * o invariante e 1 unico estorno por pagamento.
     */
    @Test
    @DisplayName("C2.a — CRITICO: corrida individual vs massa -> exatamente 1 reembolso")
    void corrida_individualVsMassa_exatamente1Reembolso() {
        Long inscricaoId = 1L;
        Long usuarioId   = 101L;
        Long eventoId    = 10L;
        Long promotorId  = 5L;

        // Seed: pagamento CONFIRMADO
        Pagamento p = Pagamento.pendente(
                inscricaoId, usuarioId, eventoId, promotorId,
                new BigDecimal("200.00"), new BigDecimal("0.1000"));
        p.confirmar("SIM-corrida");
        pagamentoRepository.saveAndFlush(p);

        // Dispara inscricao.cancelada E evento.cancelado quase simultaneamente
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();

        InscricaoCanceladaEvent msgIndividual = new InscricaoCanceladaEvent(
                eventId1, inscricaoId, usuarioId, eventoId, OffsetDateTime.now());
        EventoCanceladoEvent msgMassa = new EventoCanceladoEvent(
                eventId2, eventoId, promotorId, OffsetDateTime.now());

        // Publica ambos quase simultaneamente
        rabbitTemplate.convertAndSend(EXCHANGE, RK_INSCRICAO_CANCELADA, msgIndividual);
        rabbitTemplate.convertAndSend(EXCHANGE, RK_EVENTO_CANCELADO, msgMassa);

        // Aguarda processamento
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            // Ambos processed_events devem ter sido gravados
            assertThat(processedEventRepository.existsById(eventId1)).isTrue();
            assertThat(processedEventRepository.existsById(eventId2)).isTrue();
        });

        // INVARIANTE CRITICO: exatamente 1 reembolso (motivo pode diferir — so 1 vence)
        await().during(2, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var reembolsos = reembolsoRepository.findAll().stream()
                    .filter(r -> {
                        // Filtra reembolsos do pagamento desta inscricao
                        var pagOpt = pagamentoRepository.findByInscricaoId(inscricaoId);
                        return pagOpt.isPresent() && pagOpt.get().getId().equals(r.getPagamentoId());
                    })
                    .toList();
            assertThat(reembolsos)
                    .as("Exatamente 1 reembolso para o pagamento (nunca 2)")
                    .hasSize(1);
        });

        // Pagamento deve estar REEMBOLSADO (nunca duplicado)
        var pagamentoFinal = pagamentoRepository.findByInscricaoId(inscricaoId).orElseThrow();
        assertThat(pagamentoFinal.getStatus()).isEqualTo(StatusPagamento.REEMBOLSADO);
    }
}
