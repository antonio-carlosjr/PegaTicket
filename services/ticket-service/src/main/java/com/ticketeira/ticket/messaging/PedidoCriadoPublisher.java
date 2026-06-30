package com.ticketeira.ticket.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica o evento pedido.criado na exchange ticketeira.events (topic).
 * Deve ser chamado SOMENTE via afterCommit (TransactionSynchronizationManager),
 * nunca dentro de uma transacao — garante que o evento nao sai em rollback.
 */
@Component
public class PedidoCriadoPublisher {

    private static final Logger log = LoggerFactory.getLogger(PedidoCriadoPublisher.class);

    private static final String EXCHANGE = "ticketeira.events";
    private static final String ROUTING_KEY = "pedido.criado";

    private final RabbitTemplate rabbitTemplate;

    public PedidoCriadoPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicar(PedidoCriadoEvent evento) {
        log.debug("Publicando pedido.criado: inscricaoId={} eventId={}",
                evento.inscricaoId(), evento.eventId());
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, evento);
    }
}
