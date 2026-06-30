package com.ticketeira.payment.messaging;

import com.ticketeira.payment.service.PagamentoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor da fila pedido.criado.
 * Cria o Pagamento PENDENTE com calculo de escrow.
 * Idempotente via processed_events (PK UUID do payload).
 * Sem broker (RabbitAutoConfiguration excluido), o @RabbitListener nao e processado
 * (sem o bean post-processor) — o bean existe mas fica inerte. Com broker, e ligado.
 */
@Component
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
