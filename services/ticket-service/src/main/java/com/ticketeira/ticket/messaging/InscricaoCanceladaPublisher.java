package com.ticketeira.ticket.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Publica o evento inscricao.cancelada na exchange ticketeira.events (topic).
 * Deve ser chamado SOMENTE via afterCommit (TransactionSynchronizationManager),
 * nunca dentro de uma transacao — garante que o evento nao sai em rollback.
 * Consumidor unico: payment-service (reembolso individual, US-035 PAGO).
 *
 * Injecao OPCIONAL do RabbitTemplate (igual ao PagamentoAprovadoPublisher do payment):
 * no perfil "test" (H2, RabbitAutoConfiguration excluido) nao ha RabbitTemplate, entao o
 * publisher opera em no-op e o contexto sobe sem precisar mockar este bean em todo
 * @SpringBootTest. Com broker (test-postgres / prod), o setter injeta e a publicacao ocorre.
 */
@Component
public class InscricaoCanceladaPublisher {

    private static final Logger log = LoggerFactory.getLogger(InscricaoCanceladaPublisher.class);

    private static final String EXCHANGE = "ticketeira.events";
    private static final String ROUTING_KEY = "inscricao.cancelada";

    private RabbitTemplate rabbitTemplate;

    @Autowired(required = false)
    public void setRabbitTemplate(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicar(InscricaoCanceladaEvent evento) {
        if (rabbitTemplate == null) {
            log.debug("RabbitTemplate indisponivel — inscricao.cancelada nao publicada (no-op).");
            return;
        }
        log.debug("Publicando inscricao.cancelada: inscricaoId={} eventId={}",
                evento.inscricaoId(), evento.eventId());
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, evento);
    }
}
