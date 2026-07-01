package com.ticketeira.ticket.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Publica o evento pedido.criado na exchange ticketeira.events (topic).
 * Deve ser chamado SOMENTE via afterCommit (TransactionSynchronizationManager),
 * nunca dentro de uma transacao — garante que o evento nao sai em rollback.
 *
 * Injecao OPCIONAL do RabbitTemplate (igual ao InscricaoCanceladaPublisher e ao
 * PagamentoAprovadoPublisher do payment): no perfil "test" (H2, RabbitAutoConfiguration
 * excluido) nao ha RabbitTemplate, entao o publisher opera em no-op e qualquer
 * @SpringBootTest sobe o contexto sem precisar mockar este bean. Com broker
 * (test-postgres / prod), o setter injeta e a publicacao ocorre.
 */
@Component
public class PedidoCriadoPublisher {

    private static final Logger log = LoggerFactory.getLogger(PedidoCriadoPublisher.class);

    private static final String EXCHANGE = "ticketeira.events";
    private static final String ROUTING_KEY = "pedido.criado";

    private RabbitTemplate rabbitTemplate;

    @Autowired(required = false)
    public void setRabbitTemplate(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicar(PedidoCriadoEvent evento) {
        if (rabbitTemplate == null) {
            // Em producao o RabbitTemplate SEMPRE existe (autoconfig ativo); um null aqui significa
            // que a autoconfig do broker falhou. A saga de pagamento nunca iniciaria em silencio —
            // por isso WARN (nao debug): a perda precisa ser visivel na operacao. (CR-5B-01)
            log.warn("RabbitTemplate indisponivel — pedido.criado NAO publicado (saga de pagamento "
                    + "nao sera iniciada): inscricaoId={} eventId={}",
                    evento.inscricaoId(), evento.eventId());
            return;
        }
        log.debug("Publicando pedido.criado: inscricaoId={} eventId={}",
                evento.inscricaoId(), evento.eventId());
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, evento);
    }
}
