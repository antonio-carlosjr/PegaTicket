package com.ticketeira.ticket.messaging;

import com.ticketeira.ticket.TestcontainersBase;
import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.client.EventResumo;
import com.ticketeira.ticket.domain.Inscricao;
import com.ticketeira.ticket.repository.IngressoRepository;
import com.ticketeira.ticket.repository.InscricaoRepository;
import com.ticketeira.ticket.service.CancelamentoInscricaoService;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * B4 — Publicacao afterCommit de inscricao.cancelada (Testcontainers PG+Rabbit). [US-035 / ADR-T11]
 * Casos: B4.a B4.b
 *
 * Verifica que a publicacao e afterCommit (rollback nao publica).
 * Espelha InscricaoAfterCommitRollbackTest da S4.
 *
 * VERMELHO: CancelamentoInscricaoService, InscricaoCanceladaPublisher nao existem.
 */
@Tag("integracao")
@SpringBootTest
@ActiveProfiles("test-postgres")
@DisplayName("B4 — inscricao.cancelada publicada afterCommit (Testcontainers PG+Rabbit)")
class InscricaoCanceladaAfterCommitTest extends TestcontainersBase {

    private static final String EXCHANGE                 = "ticketeira.events";
    private static final String RK_INSCRICAO_CANCELADA   = "inscricao.cancelada";
    private static final String QUEUE_INSCRICAO_CANCELADA = "inscricao.cancelada";

    @Autowired
    CancelamentoInscricaoService cancelamentoService;

    @Autowired
    InscricaoRepository inscricaoRepository;

    @Autowired
    IngressoRepository ingressoRepository;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    RabbitAdmin rabbitAdmin;

    @MockBean
    EventClient eventClient;

    private static final Long USUARIO_ID = 10L;
    private static final Long EVENTO_ID = 42L;

    @BeforeEach
    void limpar() {
        ingressoRepository.deleteAll();
        inscricaoRepository.deleteAll();
        rabbitAdmin.purgeQueue(QUEUE_INSCRICAO_CANCELADA, false);

        when(eventClient.getEvento(anyLong()))
                .thenReturn(new EventResumo(
                        EVENTO_ID, "Show", "PAGO", "PUBLICADO",
                        10, 100, new BigDecimal("100.00"), 5L,
                        OffsetDateTime.now().plusDays(30),
                        null,
                        7
                ));
    }

    // B4.a -------------------------------------------------------------------

    /**
     * B4.a — commit OK -> mensagem inscricao.cancelada chega na fila. [US-035 / ADR-T11]
     */
    @Test
    @DisplayName("B4.a — commit OK -> inscricao.cancelada chega na fila")
    void cancelar_commitOk_publicaMensagemNaFila() {
        Inscricao ins = Inscricao.criar(USUARIO_ID, EVENTO_ID);
        ins = inscricaoRepository.save(ins);
        final Long inscricaoId = ins.getId();

        cancelamentoService.cancelar(inscricaoId, USUARIO_ID);

        // Aguarda a mensagem chegar na fila (afterCommit)
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var msg = rabbitTemplate.receive(QUEUE_INSCRICAO_CANCELADA, 500);
            assertThat(msg).as("Mensagem inscricao.cancelada deve ter chegado na fila").isNotNull();
        });
    }

    // B4.b -------------------------------------------------------------------

    /**
     * B4.b — rollback da tx local -> nenhuma mensagem publicada. [US-035 / ADR-T11]
     *
     * Simula: tenta cancelar inscricao de OUTRO usuario (403 antes do commit)
     * -> nenhuma mensagem deve aparecer na fila.
     */
    @Test
    @DisplayName("B4.b — excecao antes do commit -> nenhuma mensagem publicada")
    void cancelar_excecaoAntesDoCommit_naoPublica() throws Exception {
        // Inscricao de outro usuario — o service lanca 403 antes do commit
        Inscricao ins = Inscricao.criar(999L, EVENTO_ID);
        ins = inscricaoRepository.save(ins);
        final Long inscricaoId = ins.getId();

        try {
            cancelamentoService.cancelar(inscricaoId, USUARIO_ID);
        } catch (Exception ignored) {
            // esperado: 403 lancado
        }

        // Aguarda um pouco e verifica que nada foi publicado
        Thread.sleep(2000);
        var msg = rabbitTemplate.receive(QUEUE_INSCRICAO_CANCELADA, 200);
        assertThat(msg).as("Nenhuma mensagem deve ter sido publicada em rollback").isNull();
    }
}
