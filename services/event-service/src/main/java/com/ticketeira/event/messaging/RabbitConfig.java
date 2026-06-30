package com.ticketeira.event.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracao do RabbitMQ para o event-service (primeira vez com AMQP).
 *
 * Padrao S4 obrigatorio: delegar ao autoconfigure do Spring Boot.
 * NAO declaramos RabbitTemplate proprio. NAO usamos @ConditionalOnBean.
 * O MessageConverter e aplicado automaticamente ao RabbitTemplate e a fabrica
 * de listeners pelo Boot (RabbitAutoConfiguration).
 *
 * Declaramos exchanges/filas/bindings que espelham infra/rabbitmq/definitions.json
 * para que os testes Testcontainers tenham topologia disponivel (RabbitAdmin idempotente).
 *
 * event-service so PRODUZ — nao declara filas de consumo proprias.
 * Declaramos as filas aqui para que o TestcontainersBase possa fazer purgeQueue.
 */
@Configuration
public class RabbitConfig {

    // Nomes identicos ao definitions.json
    public static final String EXCHANGE_EVENTS = "ticketeira.events";
    public static final String EXCHANGE_DLX    = "ticketeira.dlx";

    public static final String RK_EVENTO_FINALIZADO = "evento.finalizado";
    public static final String RK_EVENTO_CANCELADO  = "evento.cancelado";

    public static final String QUEUE_EVENTO_FINALIZADO         = "evento.finalizado";
    public static final String QUEUE_EVENTO_FINALIZADO_DLQ     = "evento.finalizado.dlq";
    public static final String QUEUE_EVENTO_CANCELADO          = "evento.cancelado";
    public static final String QUEUE_EVENTO_CANCELADO_DLQ      = "evento.cancelado.dlq";
    public static final String QUEUE_EVENTO_CANCELADO_TICKET   = "evento.cancelado.ticket";
    public static final String QUEUE_EVENTO_CANCELADO_TICKET_DLQ = "evento.cancelado.ticket.dlq";

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

    // --- Fila evento.finalizado (consumida pelo payment-service) ---

    @Bean
    public Queue queueEventoFinalizado() {
        return QueueBuilder.durable(QUEUE_EVENTO_FINALIZADO)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_EVENTO_FINALIZADO)
                .build();
    }

    @Bean
    public Queue queueEventoFinalizadoDlq() {
        return QueueBuilder.durable(QUEUE_EVENTO_FINALIZADO_DLQ).build();
    }

    // --- Fila evento.cancelado (consumida pelo payment-service) ---

    @Bean
    public Queue queueEventoCancelado() {
        return QueueBuilder.durable(QUEUE_EVENTO_CANCELADO)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_EVENTO_CANCELADO)
                .build();
    }

    @Bean
    public Queue queueEventoCanceladoDlq() {
        return QueueBuilder.durable(QUEUE_EVENTO_CANCELADO_DLQ).build();
    }

    // --- Fila evento.cancelado.ticket (consumida pelo ticket-service — fan-out) ---

    @Bean
    public Queue queueEventoCanceladoTicket() {
        return QueueBuilder.durable(QUEUE_EVENTO_CANCELADO_TICKET)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_EVENTO_CANCELADO_TICKET)
                .build();
    }

    @Bean
    public Queue queueEventoCanceladoTicketDlq() {
        return QueueBuilder.durable(QUEUE_EVENTO_CANCELADO_TICKET_DLQ).build();
    }

    // --- Bindings: exchange principal -> filas ---

    @Bean
    public Binding bindingEventoFinalizado() {
        return BindingBuilder.bind(queueEventoFinalizado())
                .to(exchangeEvents())
                .with(RK_EVENTO_FINALIZADO);
    }

    @Bean
    public Binding bindingEventoCancelado() {
        return BindingBuilder.bind(queueEventoCancelado())
                .to(exchangeEvents())
                .with(RK_EVENTO_CANCELADO);
    }

    @Bean
    public Binding bindingEventoCanceladoTicket() {
        return BindingBuilder.bind(queueEventoCanceladoTicket())
                .to(exchangeEvents())
                .with(RK_EVENTO_CANCELADO);
    }

    // --- Bindings: DLX -> DLQ ---

    @Bean
    public Binding bindingEventoFinalizadoDlq() {
        return BindingBuilder.bind(queueEventoFinalizadoDlq())
                .to(exchangeDlx())
                .with(QUEUE_EVENTO_FINALIZADO);
    }

    @Bean
    public Binding bindingEventoCanceladoDlq() {
        return BindingBuilder.bind(queueEventoCanceladoDlq())
                .to(exchangeDlx())
                .with(QUEUE_EVENTO_CANCELADO);
    }

    @Bean
    public Binding bindingEventoCanceladoTicketDlq() {
        return BindingBuilder.bind(queueEventoCanceladoTicketDlq())
                .to(exchangeDlx())
                .with(QUEUE_EVENTO_CANCELADO_TICKET);
    }
}
