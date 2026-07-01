package com.ticketeira.ticket.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuracao do RabbitMQ do ticket-service.
 * Exchange e filas declarados aqui espelham infra/rabbitmq/definitions.json
 * (sem divergir da topologia de producao).
 * Converter: Jackson2JsonMessageConverter com suporte a OffsetDateTime.
 * Perfil "test" exclui RabbitAutoConfiguration — esta config so ativa quando Rabbit esta presente.
 */
@Configuration
@Profile("!test")   // excluido no perfil test (H2, sem broker real)
public class RabbitConfig {

    // Exchange principal (topic, durable)
    static final String EXCHANGE = "ticketeira.events";

    // Exchange de dead-letter
    static final String DLX = "ticketeira.dlx";

    // Filas consumidas/produzidas pelo ticket-service
    static final String QUEUE_PEDIDO_CRIADO = "pedido.criado";
    static final String QUEUE_PAGAMENTO_APROVADO = "pagamento.aprovado";

    // Fila dedicada do fan-out evento.cancelado (rk: evento.cancelado -> fila: evento.cancelado.ticket)
    static final String QUEUE_EVENTO_CANCELADO_TK = "evento.cancelado.ticket";
    static final String RK_EVENTO_CANCELADO = "evento.cancelado";

    // Fila inscricao.cancelada (ticket produz; payment consome; declarada aqui para testes Testcontainers)
    static final String QUEUE_INSCRICAO_CANCELADA = "inscricao.cancelada";
    static final String RK_INSCRICAO_CANCELADA = "inscricao.cancelada";

    @Bean
    public TopicExchange ticketeiraEvents() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange tickeiraDlx() {
        return new TopicExchange(DLX, true, false);
    }

    @Bean
    public Queue filaPedidoCriado() {
        return QueueBuilder.durable(QUEUE_PEDIDO_CRIADO)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_PEDIDO_CRIADO)
                .build();
    }

    @Bean
    public Queue filaPagamentoAprovado() {
        return QueueBuilder.durable(QUEUE_PAGAMENTO_APROVADO)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_PAGAMENTO_APROVADO)
                .build();
    }

    @Bean
    public Queue filaPedidoCriadoDlq() {
        return QueueBuilder.durable(QUEUE_PEDIDO_CRIADO + ".dlq").build();
    }

    @Bean
    public Queue filaPagamentoAprovadoDlq() {
        return QueueBuilder.durable(QUEUE_PAGAMENTO_APROVADO + ".dlq").build();
    }

    @Bean
    public Binding bindingPedidoCriado() {
        return BindingBuilder.bind(filaPedidoCriado())
                .to(ticketeiraEvents())
                .with(QUEUE_PEDIDO_CRIADO);
    }

    @Bean
    public Binding bindingPagamentoAprovado() {
        return BindingBuilder.bind(filaPagamentoAprovado())
                .to(ticketeiraEvents())
                .with(QUEUE_PAGAMENTO_APROVADO);
    }

    @Bean
    public Binding bindingPedidoCriadoDlq() {
        return BindingBuilder.bind(filaPedidoCriadoDlq())
                .to(tickeiraDlx())
                .with(QUEUE_PEDIDO_CRIADO);
    }

    @Bean
    public Binding bindingPagamentoAprovadoDlq() {
        return BindingBuilder.bind(filaPagamentoAprovadoDlq())
                .to(tickeiraDlx())
                .with(QUEUE_PAGAMENTO_APROVADO);
    }

    // --- evento.cancelado.ticket (fan-out US-042) --------------------------------

    @Bean
    public Queue filaEventoCanceladoTicket() {
        return QueueBuilder.durable(QUEUE_EVENTO_CANCELADO_TK)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_EVENTO_CANCELADO_TK)
                .build();
    }

    @Bean
    public Queue filaEventoCanceladoTicketDlq() {
        return QueueBuilder.durable(QUEUE_EVENTO_CANCELADO_TK + ".dlq").build();
    }

    @Bean
    public Binding bindingEventoCanceladoTicket() {
        return BindingBuilder.bind(filaEventoCanceladoTicket())
                .to(ticketeiraEvents())
                .with(RK_EVENTO_CANCELADO);
    }

    @Bean
    public Binding bindingEventoCanceladoTicketDlq() {
        return BindingBuilder.bind(filaEventoCanceladoTicketDlq())
                .to(tickeiraDlx())
                .with(QUEUE_EVENTO_CANCELADO_TK);
    }

    // --- inscricao.cancelada (ticket produz; payment consome; declarada p/ testes Testcontainers) ---

    @Bean
    public Queue filaInscricaoCancelada() {
        return QueueBuilder.durable(QUEUE_INSCRICAO_CANCELADA)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_INSCRICAO_CANCELADA)
                .build();
    }

    @Bean
    public Queue filaInscricaoCanceladaDlq() {
        return QueueBuilder.durable(QUEUE_INSCRICAO_CANCELADA + ".dlq").build();
    }

    @Bean
    public Binding bindingInscricaoCancelada() {
        return BindingBuilder.bind(filaInscricaoCancelada())
                .to(ticketeiraEvents())
                .with(RK_INSCRICAO_CANCELADA);
    }

    @Bean
    public Binding bindingInscricaoCanceladaDlq() {
        return BindingBuilder.bind(filaInscricaoCanceladaDlq())
                .to(tickeiraDlx())
                .with(QUEUE_INSCRICAO_CANCELADA);
    }

    // O RabbitTemplate e o ConnectionFactory sao auto-configurados pelo Spring Boot
    // (RabbitAutoConfiguration). Este bean MessageConverter e aplicado automaticamente
    // tanto ao RabbitTemplate quanto a fabrica de listeners. Declarar um RabbitTemplate
    // proprio aqui exigiria o ConnectionFactory no contexto e quebraria os testes que
    // excluem o RabbitAutoConfiguration (Postgres-only). Quando o broker esta ausente,
    // esta config so contribui beans inertes (converter/filas) — sem efeito.
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }
}
