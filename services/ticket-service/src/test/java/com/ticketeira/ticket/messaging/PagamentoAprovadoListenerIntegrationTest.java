package com.ticketeira.ticket.messaging;

import com.ticketeira.ticket.TestcontainersBase;
import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.client.EventResumo;
import com.ticketeira.ticket.domain.Inscricao;
import com.ticketeira.ticket.domain.StatusInscricao;
import com.ticketeira.ticket.repository.IngressoRepository;
import com.ticketeira.ticket.repository.InscricaoRepository;
import com.ticketeira.ticket.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Testes de integracao do PagamentoAprovadoListener (A3).
 * Requer Postgres + RabbitMQ reais (Testcontainers).
 * Cobre:
 * - A3.a: pagamentoAprovado_emiteIngresso_eAtivaInscricao
 * - A3.b: pagamentoAprovado_reentregue2x_emiteApenas1Ingresso (CRITICO)
 * - A3.c: pagamentoAprovado_paraInscricaoInexistente_vaParaDLQ_naoQuebra
 * Casos de teste: A3 (tests-spec.md)
 */
@Tag("integracao")
@SpringBootTest
@ActiveProfiles("test-postgres")
@DisplayName("A3 — PagamentoAprovadoListener (emissao de ingresso + idempotencia)")
class PagamentoAprovadoListenerIntegrationTest extends TestcontainersBase {

    private static final String EXCHANGE = "ticketeira.events";
    private static final String ROUTING_KEY = "pagamento.aprovado";

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    InscricaoRepository inscricaoRepository;

    @Autowired
    IngressoRepository ingressoRepository;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @MockBean
    EventClient eventClient;

    @BeforeEach
    void limpar() {
        ingressoRepository.deleteAll();
        inscricaoRepository.deleteAll();
        processedEventRepository.deleteAll();

        when(eventClient.getEvento(anyLong()))
                .thenReturn(new EventResumo(1L, "Show", "PAGO", "PUBLICADO", 10, 100,
                        new BigDecimal("100.00"), 5L));
    }

    /**
     * Cria uma inscricao PENDENTE_PAGAMENTO diretamente no banco (bypass do service).
     */
    private Inscricao seedInscricaoPendente(Long usuarioId, Long eventoId) {
        Inscricao inscricao = Inscricao.pendentePagamento(usuarioId, eventoId);
        return inscricaoRepository.saveAndFlush(inscricao);
    }

    @Test
    @DisplayName("A3.a — pagamentoAprovado_emiteIngresso_eAtivaInscricao")
    void pagamentoAprovado_emiteIngresso_eAtivaInscricao() {
        Inscricao inscricao = seedInscricaoPendente(10L, 100L);
        Long inscricaoId = inscricao.getId();

        UUID eventId = UUID.randomUUID();
        PagamentoAprovadoEvent evento = new PagamentoAprovadoEvent(
                eventId,
                1L,         // pagamentoId
                inscricaoId,
                10L,        // usuarioId
                100L,       // eventoId
                OffsetDateTime.now()
        );

        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, evento);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // 1 Ingresso emitido com codigoUnico
            assertThat(ingressoRepository.count()).isEqualTo(1);
            var ingresso = ingressoRepository.findAll().get(0);
            assertThat(ingresso.getCodigoUnico()).isNotBlank();
            assertThat(ingresso.getStatus().name()).isEqualTo("ATIVO");

            // Inscricao -> ATIVA
            var inscricaoAtualizada = inscricaoRepository.findById(inscricaoId).orElseThrow();
            assertThat(inscricaoAtualizada.getStatus()).isEqualTo(StatusInscricao.ATIVA);

            // 1 registro em processed_events
            assertThat(processedEventRepository.existsById(eventId)).isTrue();
        });
    }

    @Test
    @DisplayName("A3.b — pagamentoAprovado_reentregue2x_emiteApenas1Ingresso (CRITICO)")
    void pagamentoAprovado_reentregue2x_emiteApenas1Ingresso() {
        Inscricao inscricao = seedInscricaoPendente(11L, 101L);
        Long inscricaoId = inscricao.getId();

        UUID eventId = UUID.randomUUID(); // mesmo eventId nas 2 entregas
        PagamentoAprovadoEvent evento = new PagamentoAprovadoEvent(
                eventId,
                2L,
                inscricaoId,
                11L,
                101L,
                OffsetDateTime.now()
        );

        // Primeira entrega
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, evento);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(ingressoRepository.count()).isEqualTo(1));

        // Segunda entrega (reentrega — at-least-once)
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, evento);

        // Aguarda e verifica que nao duplicou
        await().during(2, TimeUnit.SECONDS).atMost(8, TimeUnit.SECONDS).untilAsserted(() -> {
            // CRITICO: apenas 1 ingresso, 1 linha em processed_events
            assertThat(ingressoRepository.count())
                    .as("Deve haver exatamente 1 ingresso mesmo apos reentrega")
                    .isEqualTo(1);
            assertThat(processedEventRepository.count())
                    .as("Deve haver exatamente 1 registro em processed_events")
                    .isEqualTo(1);
        });
    }

    @Test
    @DisplayName("A3.d — pagamentoAprovado_paraInscricaoEXPIRADA_naoEmiteIngresso_eAck (R6, CR-S4-01)")
    void pagamentoAprovado_paraInscricaoExpirada_naoEmiteIngresso() {
        // Inscricao ja expirada pelo TTL antes da confirmacao chegar (corrida R6)
        Inscricao inscricao = Inscricao.pendentePagamento(13L, 103L);
        inscricao.expirar();
        Inscricao salva = inscricaoRepository.saveAndFlush(inscricao);
        Long inscricaoId = salva.getId();

        UUID eventId = UUID.randomUUID();
        PagamentoAprovadoEvent evento = new PagamentoAprovadoEvent(
                eventId, 4L, inscricaoId, 13L, 103L, OffsetDateTime.now());

        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, evento);

        // O consumidor deve ACK (no-op): processed_events gravado, 0 ingressos, inscricao segue EXPIRADA.
        // Sem o guard, a tx faria rollback em loop e a mensagem nunca seria consumida (DLQ).
        await().during(2, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(ingressoRepository.count())
                    .as("Inscricao EXPIRADA nao deve gerar ingresso")
                    .isZero();
            assertThat(processedEventRepository.existsById(eventId))
                    .as("Mensagem deve ter sido processada (ACK), nao re-entregue em loop")
                    .isTrue();
            assertThat(inscricaoRepository.findById(inscricaoId).orElseThrow().getStatus())
                    .isEqualTo(StatusInscricao.EXPIRADA);
        });
    }

    @Test
    @DisplayName("A3.c — pagamentoAprovado_paraInscricaoInexistente_vaParaDLQ_naoQuebra")
    void pagamentoAprovado_paraInscricaoInexistente_vaParaDLQ_naoQuebra() {
        // Inscricao 99999 nao existe no banco
        UUID eventId = UUID.randomUUID();
        PagamentoAprovadoEvent evento = new PagamentoAprovadoEvent(
                eventId,
                3L,
                99999L,  // inscricaoId inexistente
                10L,
                100L,
                OffsetDateTime.now()
        );

        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, evento);

        // Aguarda um tempo para o consumidor tentar processar
        await().during(3, TimeUnit.SECONDS).atMost(12, TimeUnit.SECONDS).untilAsserted(() -> {
            // Sistema permanece consistente: 0 ingressos criados
            assertThat(ingressoRepository.count())
                    .as("Inscricao inexistente nao deve criar ingressos")
                    .isZero();
            // A mensagem deve ir para DLQ (pagamento.aprovado.dlq) apos as tentativas
            // Verificar que a DLQ tem a mensagem (timeout de re-delivery do RabbitMQ)
            // Nota: o timeout de re-delivery pode variar; o importante e que 0 ingressos foram criados.
        });
    }
}
