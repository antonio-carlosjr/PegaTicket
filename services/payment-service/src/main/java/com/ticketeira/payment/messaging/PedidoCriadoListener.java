package com.ticketeira.payment.messaging;

import com.ticketeira.payment.service.PagamentoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Consumidor da fila pedido.criado.
 * Cria o Pagamento PENDENTE com calculo de escrow.
 * Idempotente via processed_events (PK UUID do payload).
 * Condicional: so criado quando o broker RabbitMQ esta disponivel.
 */
@Component
@ConditionalOnBean(ConnectionFactory.class)
public class PedidoCriadoListener {

    private static final Logger log = LoggerFactory.getLogger(PedidoCriadoListener.class);

    private final PagamentoService pagamentoService;

    public PedidoCriadoListener(PagamentoService pagamentoService) {
        this.pagamentoService = pagamentoService;
    }

    @RabbitListener(queues = "pedido.criado")
    public void onPedidoCriado(PedidoCriadoEvent evento) {
        log.info("Recebido pedido.criado: eventId={}, inscricaoId={}",
                evento.eventId(), evento.inscricaoId());
        pagamentoService.criarPendente(evento);
    }
}
