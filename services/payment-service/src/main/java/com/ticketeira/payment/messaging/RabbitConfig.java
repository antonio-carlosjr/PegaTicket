package com.ticketeira.payment.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracao do RabbitMQ.
 * Declara topologia identica ao infra/rabbitmq/definitions.json para que
 * os testes Testcontainers tenham as filas/bindings disponiveis.
 * Os beans de topologia (filas/exchanges/bindings) sao condicionais ao ConnectionFactory
 * para nao falhar quando RabbitAutoConfiguration e excluida (perfil test H2).
 */
@Configuration
public class RabbitConfig {

    // Nomes identicos ao definitions.json
    public static final String EXCHANGE_EVENTS = "ticketeira.events";
    public static final String EXCHANGE_DLX = "ticketeira.dlx";

    public static final String QUEUE_PEDIDO_CRIADO = "pedido.criado";
    public static final String QUEUE_PAGAMENTO_APROVADO = "pagamento.aprovado";
    public static final String QUEUE_EVENTO_FINALIZADO = "evento.finalizado";

    public static final String QUEUE_PEDIDO_CRIADO_DLQ = "pedido.criado.dlq";
    public static final String QUEUE_PAGAMENTO_APROVADO_DLQ = "pagamento.aprovado.dlq";
    public static final String QUEUE_EVENTO_FINALIZADO_DLQ = "evento.finalizado.dlq";

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Sobrescreve o RabbitTemplate auto-configurado para usar o Jackson converter.
     * Condicional: so criado quando o ConnectionFactory existe (Rabbit ativo).
     */
    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    // --- Exchanges (condicionais ao ConnectionFactory) ---

    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    public TopicExchange exchangeEvents() {
        return ExchangeBuilder.topicExchange(EXCHANGE_EVENTS).durable(true).build();
    }

    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    public TopicExchange exchangeDlx() {
        return ExchangeBuilder.topicExchange(EXCHANGE_DLX).durable(true).build();
    }

    // --- Filas principais com DLX ---

    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    public Queue queuePedidoCriado() {
        return QueueBuilder.durable(QUEUE_PEDIDO_CRIADO)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_PEDIDO_CRIADO)
                .build();
    }

    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    public Queue queuePagamentoAprovado() {
        return QueueBuilder.durable(QUEUE_PAGAMENTO_APROVADO)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_PAGAMENTO_APROVADO)
                .build();
    }

    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    public Queue queueEventoFinalizado() {
        return QueueBuilder.durable(QUEUE_EVENTO_FINALIZADO)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_EVENTO_FINALIZADO)
                .build();
    }

    // --- Filas DLQ ---

    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    public Queue queuePedidoCriadoDlq() {
        return QueueBuilder.durable(QUEUE_PEDIDO_CRIADO_DLQ).build();
    }

    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    public Queue queuePagamentoAprovadoDlq() {
        return QueueBuilder.durable(QUEUE_PAGAMENTO_APROVADO_DLQ).build();
    }

    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    public Queue queueEventoFinalizadoDlq() {
        return QueueBuilder.durable(QUEUE_EVENTO_FINALIZADO_DLQ).build();
    }

    // --- Bindings: exchange principal -> filas ---

    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    public Binding bindingPedidoCriado() {
        return BindingBuilder.bind(queuePedidoCriado())
                .to(exchangeEvents())
                .with(QUEUE_PEDIDO_CRIADO);
    }

    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    public Binding bindingPagamentoAprovado() {
        return BindingBuilder.bind(queuePagamentoAprovado())
                .to(exchangeEvents())
                .with(QUEUE_PAGAMENTO_APROVADO);
    }

    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    public Binding bindingEventoFinalizado() {
        return BindingBuilder.bind(queueEventoFinalizado())
                .to(exchangeEvents())
                .with(QUEUE_EVENTO_FINALIZADO);
    }

    // --- Bindings: DLX -> DLQ ---

    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    public Binding bindingPedidoCriadoDlq() {
        return BindingBuilder.bind(queuePedidoCriadoDlq())
                .to(exchangeDlx())
                .with(QUEUE_PEDIDO_CRIADO);
    }

    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    public Binding bindingPagamentoAprovadoDlq() {
        return BindingBuilder.bind(queuePagamentoAprovadoDlq())
                .to(exchangeDlx())
                .with(QUEUE_PAGAMENTO_APROVADO);
    }

    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    public Binding bindingEventoFinalizadoDlq() {
        return BindingBuilder.bind(queueEventoFinalizadoDlq())
                .to(exchangeDlx())
                .with(QUEUE_EVENTO_FINALIZADO);
    }
}
