package com.ticketeira.payment.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracao do RabbitMQ.
 * Declara o converter JSON e a topologia identica ao infra/rabbitmq/definitions.json
 * (para que os testes Testcontainers tenham filas/bindings disponiveis).
 *
 * O RabbitTemplate e o ConnectionFactory sao AUTO-CONFIGURADOS pelo Spring Boot
 * (RabbitAutoConfiguration). O MessageConverter abaixo e aplicado automaticamente
 * tanto ao RabbitTemplate quanto a fabrica de listeners. NAO declaramos um
 * RabbitTemplate proprio nem condicionamos os beans ao ConnectionFactory: a versao
 * anterior usava @ConditionalOnBean(ConnectionFactory.class) em config de usuario,
 * que e avaliado ANTES do autoconfigure registrar o ConnectionFactory — pulando os
 * beans ate quando o broker existe. Sem broker (perfil test H2 / Postgres-only sem
 * RabbitAutoConfiguration), estes beans ficam inertes (nenhum RabbitAdmin os declara)
 * e nao exigem ConnectionFactory.
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

    // --- Exchanges ---

    @Bean
    public TopicExchange exchangeEvents() {
        return ExchangeBuilder.topicExchange(EXCHANGE_EVENTS).durable(true).build();
    }

    @Bean
    public TopicExchange exchangeDlx() {
        return ExchangeBuilder.topicExchange(EXCHANGE_DLX).durable(true).build();
    }

    // --- Filas principais com DLX ---

    @Bean
    public Queue queuePedidoCriado() {
        return QueueBuilder.durable(QUEUE_PEDIDO_CRIADO)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_PEDIDO_CRIADO)
                .build();
    }

    @Bean
    public Queue queuePagamentoAprovado() {
        return QueueBuilder.durable(QUEUE_PAGAMENTO_APROVADO)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_PAGAMENTO_APROVADO)
                .build();
    }

    @Bean
    public Queue queueEventoFinalizado() {
        return QueueBuilder.durable(QUEUE_EVENTO_FINALIZADO)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_EVENTO_FINALIZADO)
                .build();
    }

    // --- Filas DLQ ---

    @Bean
    public Queue queuePedidoCriadoDlq() {
        return QueueBuilder.durable(QUEUE_PEDIDO_CRIADO_DLQ).build();
    }

    @Bean
    public Queue queuePagamentoAprovadoDlq() {
        return QueueBuilder.durable(QUEUE_PAGAMENTO_APROVADO_DLQ).build();
    }

    @Bean
    public Queue queueEventoFinalizadoDlq() {
        return QueueBuilder.durable(QUEUE_EVENTO_FINALIZADO_DLQ).build();
    }

    // --- Bindings: exchange principal -> filas ---

    @Bean
    public Binding bindingPedidoCriado() {
        return BindingBuilder.bind(queuePedidoCriado()).to(exchangeEvents()).with(QUEUE_PEDIDO_CRIADO);
    }

    @Bean
    public Binding bindingPagamentoAprovado() {
        return BindingBuilder.bind(queuePagamentoAprovado()).to(exchangeEvents()).with(QUEUE_PAGAMENTO_APROVADO);
    }

    @Bean
    public Binding bindingEventoFinalizado() {
        return BindingBuilder.bind(queueEventoFinalizado()).to(exchangeEvents()).with(QUEUE_EVENTO_FINALIZADO);
    }

    // --- Bindings: DLX -> DLQ ---

    @Bean
    public Binding bindingPedidoCriadoDlq() {
        return BindingBuilder.bind(queuePedidoCriadoDlq()).to(exchangeDlx()).with(QUEUE_PEDIDO_CRIADO);
    }

    @Bean
    public Binding bindingPagamentoAprovadoDlq() {
        return BindingBuilder.bind(queuePagamentoAprovadoDlq()).to(exchangeDlx()).with(QUEUE_PAGAMENTO_APROVADO);
    }

    @Bean
    public Binding bindingEventoFinalizadoDlq() {
        return BindingBuilder.bind(queueEventoFinalizadoDlq()).to(exchangeDlx()).with(QUEUE_EVENTO_FINALIZADO);
    }
}
