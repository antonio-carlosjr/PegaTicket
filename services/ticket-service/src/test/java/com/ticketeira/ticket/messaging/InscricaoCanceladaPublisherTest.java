package com.ticketeira.ticket.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * CR-5B-01 — invariantes do InscricaoCanceladaPublisher (injecao opcional do RabbitTemplate).
 *
 * Trava o comportamento do publisher com RabbitTemplate OPCIONAL (setter @Autowired(required=false)):
 *  - com template presente: publica no exchange/routing key corretos (o reembolso individual e disparado);
 *  - com template null (autoconfig do broker ausente/falha): NAO publica e NAO lanca (no-op silencioso p/
 *    o chamador, mas registrado em WARN — ver o log elevado a WARN no proprio publisher).
 *
 * Unit puro (Mockito) — nao depende de Testcontainers; roda no CI e localmente.
 */
@Tag("unit")
@DisplayName("CR-5B-01 — InscricaoCanceladaPublisher: injecao opcional do RabbitTemplate")
class InscricaoCanceladaPublisherTest {

    private static final String EXCHANGE = "ticketeira.events";
    private static final String ROUTING_KEY = "inscricao.cancelada";

    private InscricaoCanceladaEvent evento() {
        return new InscricaoCanceladaEvent(
                UUID.randomUUID(), 17L, 24L, 10L, OffsetDateTime.now());
    }

    @Test
    @DisplayName("com RabbitTemplate presente -> publica no exchange/rk corretos")
    void publicar_comTemplate_publica() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        InscricaoCanceladaPublisher publisher = new InscricaoCanceladaPublisher();
        publisher.setRabbitTemplate(template);

        InscricaoCanceladaEvent ev = evento();
        publisher.publicar(ev);

        verify(template).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY), eq(ev));
    }

    @Test
    @DisplayName("sem RabbitTemplate (null) -> no-op, nao lanca, nao publica")
    void publicar_semTemplate_noOpNaoLanca() {
        InscricaoCanceladaPublisher publisher = new InscricaoCanceladaPublisher();
        // nenhum setRabbitTemplate: rabbitTemplate permanece null (autoconfig ausente)

        assertThatCode(() -> publisher.publicar(evento())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("setRabbitTemplate(null) explicito -> ainda no-op, nao lanca")
    void publicar_templateNullExplicito_noOp() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        InscricaoCanceladaPublisher publisher = new InscricaoCanceladaPublisher();
        publisher.setRabbitTemplate(null);

        assertThatCode(() -> publisher.publicar(evento())).doesNotThrowAnyException();
        verify(template, never()).convertAndSend(any(), any(), (Object) any());
    }
}
