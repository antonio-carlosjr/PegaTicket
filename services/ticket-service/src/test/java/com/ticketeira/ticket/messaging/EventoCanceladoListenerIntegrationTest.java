package com.ticketeira.ticket.messaging;

import com.ticketeira.ticket.TestcontainersBase;
import com.ticketeira.ticket.domain.Ingresso;
import com.ticketeira.ticket.domain.Inscricao;
import com.ticketeira.ticket.domain.StatusIngresso;
import com.ticketeira.ticket.domain.StatusInscricao;
import com.ticketeira.ticket.repository.IngressoRepository;
import com.ticketeira.ticket.repository.InscricaoRepository;
import com.ticketeira.ticket.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * D1/D2 — ticket-service: consumir evento.cancelado.ticket.
 * Casos: D1.a D1.b D1.c D1.d D2 (tests-spec.md, US-042).
 * Requer Testcontainers Postgres + RabbitMQ reais.
 *
 * VERMELHO: EventoCanceladoListener (ticket), Inscricao.cancelarPorEvento(),
 * Ingresso.cancelar(), queries em massa por evento_id ainda nao existem.
 */
@Tag("integracao")
@SpringBootTest
@ActiveProfiles("test-postgres")
@DisplayName("D1/D2 — evento.cancelado.ticket → cancelar inscricoes e ingressos")
class EventoCanceladoListenerIntegrationTest extends TestcontainersBase {

    // O ticket-service escuta a fila DEDICADA evento.cancelado.ticket (fan-out)
    private static final String EXCHANGE            = "ticketeira.events";
    private static final String RK_CANCELADO        = "evento.cancelado"; // routing key
    private static final String QUEUE_CANCELADO_TK  = "evento.cancelado.ticket";
    private static final String QUEUE_APROVADO      = "pagamento.aprovado";

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    RabbitAdmin rabbitAdmin;

    @Autowired
    InscricaoRepository inscricaoRepository;

    @Autowired
    IngressoRepository ingressoRepository;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    // ticket-service chama EventClient para reservar/liberar vagas;
    // nos testes nao queremos depender do event-service real.
    @MockBean
    com.ticketeira.ticket.client.EventClient eventClient;

    @BeforeEach
    void limpar() {
        ingressoRepository.deleteAll();
        inscricaoRepository.deleteAll();
        processedEventRepository.deleteAll();
        // Aprendizado S4: purgar filas para isolamento.
        rabbitAdmin.purgeQueue(QUEUE_CANCELADO_TK, false);
        rabbitAdmin.purgeQueue(QUEUE_APROVADO, false);
    }

    /** Seed: ATIVA com ingresso ATIVO. */
    private Inscricao seedInscricaoAtiva(Long usuarioId, Long eventoId) {
        Inscricao i = Inscricao.criar(usuarioId, eventoId);
        i = inscricaoRepository.saveAndFlush(i);
        Ingresso ing = Ingresso.emitir(i.getId());
        ingressoRepository.saveAndFlush(ing);
        return i;
    }

    /** Seed: PENDENTE_PAGAMENTO sem ingresso. */
    private Inscricao seedInscricaoPendente(Long usuarioId, Long eventoId) {
        Inscricao i = Inscricao.pendentePagamento(usuarioId, eventoId);
        return inscricaoRepository.saveAndFlush(i);
    }

    /** Seed: ATIVA com ingresso UTILIZADO (check-in). */
    private Inscricao seedInscricaoComIngressoUtilizado(Long usuarioId, Long eventoId) {
        Inscricao i = Inscricao.criar(usuarioId, eventoId);
        i = inscricaoRepository.saveAndFlush(i);
        Ingresso ing = Ingresso.emitir(i.getId());
        ing.utilizar(); // metodo de check-in — VERMELHO se nao existir
        ingressoRepository.saveAndFlush(ing);
        return i;
    }

    /** Publica evento.cancelado com routing key evento.cancelado (fan-out para .ticket). */
    private UUID publicarEventoCancelado(Long eventoId) {
        UUID eventId = UUID.randomUUID();
        // O record EventoCanceladoEvent deve ser importado/criado — VERMELHO
        EventoCanceladoEvent msg = new EventoCanceladoEvent(
                eventId, eventoId, 7L, OffsetDateTime.now());
        // Publica na routing key evento.cancelado; o binding envia copia para evento.cancelado.ticket
        rabbitTemplate.convertAndSend(EXCHANGE, "evento.cancelado", msg);
        return eventId;
    }

    // ---- D1.a ---------------------------------------------------------------

    /**
     * D1.a — eventoCancelado_cancelaInscricoesEIngressos [US-042] CRITICO
     * Evento 10: 2 ATIVA (c/ ingresso ATIVO) + 1 PENDENTE_PAGAMENTO (sem ingresso)
     * → 3 inscricoes CANCELADA; 2 ingressos CANCELADO; 1 processed_event.
     */
    @Test
    @DisplayName("D1.a — CRITICO: evento.cancelado → inscricoes CANCELADA + ingressos CANCELADO")
    void eventoCancelado_cancelaInscricoesEIngressos() {
        Long eventoId = 10L;

        Inscricao i1 = seedInscricaoAtiva(101L, eventoId);
        Inscricao i2 = seedInscricaoAtiva(102L, eventoId);
        Inscricao i3 = seedInscricaoPendente(103L, eventoId);

        UUID eventId = publicarEventoCancelado(eventoId);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            // 3 inscricoes → CANCELADA
            var inscricoes = inscricaoRepository.findAll().stream()
                    .filter(i -> eventoId.equals(i.getEventoId()))
                    .toList();
            assertThat(inscricoes).hasSize(3);
            inscricoes.forEach(i ->
                    assertThat(i.getStatus())
                            .as("Inscricao %d deve ser CANCELADA", i.getId())
                            .isEqualTo(StatusInscricao.CANCELADA));

            // 2 ingressos → CANCELADO
            var ingressos = ingressoRepository.findAll();
            assertThat(ingressos).hasSize(2); // a PENDENTE nao tinha ingresso
            ingressos.forEach(ing ->
                    assertThat(ing.getStatus())
                            .as("Ingresso %d deve ser CANCELADO", ing.getId())
                            .isEqualTo(StatusIngresso.CANCELADO));

            // 1 processed_event
            assertThat(processedEventRepository.existsById(eventId)).isTrue();
        });
    }

    // ---- D1.b ---------------------------------------------------------------

    /**
     * D1.b — eventoCancelado_reentregue2x_cancelaApenas1Vez [US-042]
     * 2x mesma mensagem → estados finais identicos; 1 processed_event (idempotente).
     */
    @Test
    @DisplayName("D1.b — reentrega de evento.cancelado → idempotente (nao duplica cancelamentos)")
    void eventoCancelado_reentregue2x_cancelaApenas1Vez() {
        Long eventoId = 20L;
        seedInscricaoAtiva(110L, eventoId);
        seedInscricaoAtiva(111L, eventoId);

        UUID eventId = UUID.randomUUID();
        EventoCanceladoEvent msg = new EventoCanceladoEvent(
                eventId, eventoId, 7L, OffsetDateTime.now());

        // Primeira entrega
        rabbitTemplate.convertAndSend(EXCHANGE, "evento.cancelado", msg);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            long canceladas = inscricaoRepository.findAll().stream()
                    .filter(i -> i.getStatus() == StatusInscricao.CANCELADA).count();
            assertThat(canceladas).isEqualTo(2);
        });

        // Segunda entrega (reentrega)
        rabbitTemplate.convertAndSend(EXCHANGE, "evento.cancelado", msg);

        await().during(3, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // Estados identicos apos reentrega
            long canceladas = inscricaoRepository.findAll().stream()
                    .filter(i -> i.getStatus() == StatusInscricao.CANCELADA).count();
            assertThat(canceladas).as("Reentrega nao deve alterar contagem").isEqualTo(2);
            assertThat(processedEventRepository.count())
                    .as("1 processed_event mesmo apos reentrega").isEqualTo(1);
        });
    }

    // ---- D1.c ---------------------------------------------------------------

    /**
     * D1.c — eventoCancelado_naoTocaInscricoesDeOutroEvento [US-042]
     * Inscricao ATIVA do evento 20 permanece ATIVA.
     */
    @Test
    @DisplayName("D1.c — inscricoes de outro evento nao sao tocadas")
    void eventoCancelado_naoTocaInscricoesDeOutroEvento() {
        Long eventoId = 10L;
        Long outroEventoId = 20L;

        seedInscricaoAtiva(101L, eventoId);
        Inscricao outroEvento = seedInscricaoAtiva(102L, outroEventoId);

        publicarEventoCancelado(eventoId);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var i10 = inscricaoRepository.findAll().stream()
                    .filter(i -> eventoId.equals(i.getEventoId()))
                    .findFirst().orElseThrow();
            assertThat(i10.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
        });

        // Inscricao do outro evento permanece ATIVA
        var iOutro = inscricaoRepository.findById(outroEvento.getId()).orElseThrow();
        assertThat(iOutro.getStatus())
                .as("Inscricao do evento 20 deve permanecer ATIVA")
                .isEqualTo(StatusInscricao.ATIVA);
    }

    // ---- D1.d ---------------------------------------------------------------

    /**
     * D1.d — eventoCancelado_ingressoUtilizado_naoVoltaParaCancelado [US-042]
     * Ingresso ja UTILIZADO (check-in) nao e tocado pelo cancelamento em massa.
     * Preserva historico de check-in (UPDATE so toca status='ATIVO').
     */
    @Test
    @DisplayName("D1.d — ingresso UTILIZADO nao e cancelado (protege historico de check-in)")
    void eventoCancelado_ingressoUtilizado_naoVoltaParaCancelado() {
        Long eventoId = 30L;
        Inscricao i = seedInscricaoComIngressoUtilizado(120L, eventoId);

        publicarEventoCancelado(eventoId);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // Inscricao CANCELADA (cancelamento e aplicado)
            var inscricao = inscricaoRepository.findById(i.getId()).orElseThrow();
            assertThat(inscricao.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
        });

        // Ingresso UTILIZADO preservado
        var ingressos = ingressoRepository.findAll().stream()
                .filter(ing -> i.getId().equals(ing.getInscricaoId()))
                .toList();
        assertThat(ingressos).hasSize(1);
        assertThat(ingressos.get(0).getStatus())
                .as("Ingresso UTILIZADO deve ser preservado")
                .isEqualTo(StatusIngresso.UTILIZADO);
    }

    // ---- D2 -----------------------------------------------------------------

    /**
     * D2 — eventoCancelado_eventoSemInscricoes_ackNoop [US-042]
     * Evento sem inscricoes → 0 linhas, ACK, processed_event gravado, sem DLQ.
     */
    @Test
    @DisplayName("D2 — evento sem inscricoes → ACK no-op, processed_event gravado")
    void eventoCancelado_eventoSemInscricoes_ackNoop() {
        Long eventoId = 999L; // sem inscricoes

        UUID eventId = publicarEventoCancelado(eventoId);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(processedEventRepository.existsById(eventId))
                        .as("processed_event gravado mesmo sem inscricoes").isTrue());

        assertThat(inscricaoRepository.count()).isZero();
        assertThat(ingressoRepository.count()).isZero();
    }
}
