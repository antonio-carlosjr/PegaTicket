package com.ticketeira.payment.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Publica pagamento.aprovado no exchange ticketeira.events apos o commit da transacao.
 * Garante que a mensagem so vai para o broker se a tx for commitada (afterCommit).
 * Quando RabbitTemplate nao esta disponivel (perfil test H2), opera em modo no-op.
 */
@Component
public class PagamentoAprovadoPublisher {

    private static final Logger log = LoggerFactory.getLogger(PagamentoAprovadoPublisher.class);
    private static final String EXCHANGE = "ticketeira.events";
    private static final String ROUTING_KEY = "pagamento.aprovado";

    private RabbitTemplate rabbitTemplate;

    /**
     * Injecao opcional: sem RabbitTemplate (perfil test H2), o publisher funciona em modo no-op.
     */
    @Autowired(required = false)
    public void setRabbitTemplate(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Construtor para injecao direta nos testes unitarios (AfterCommitRollbackTest).
     * Quando chamado diretamente, o RabbitTemplate e o mock injetado.
     */
    public PagamentoAprovadoPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Construtor padrao para uso pelo Spring (injecao via setter acima).
     */
    public PagamentoAprovadoPublisher() {
    }

    /**
     * Registra o envio para ocorrer somente apos o commit da transacao corrente.
     * Se nao houver transacao ativa, publica imediatamente (testes unitarios sem Spring).
     */
    public void publicarAposCommit(Long pagamentoId, Long inscricaoId, Long usuarioId, Long eventoId) {
        PagamentoAprovadoEvent evento = new PagamentoAprovadoEvent(
                UUID.randomUUID(),
                pagamentoId,
                inscricaoId,
                usuarioId,
                eventoId,
                OffsetDateTime.now()
        );

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviar(evento);
                }
            });
        } else {
            // Sem tx ativa (ex: testes unitarios com mock de txManager) — publica direto
            enviar(evento);
        }
    }

    private void enviar(PagamentoAprovadoEvent evento) {
        if (rabbitTemplate == null) {
            log.debug("RabbitTemplate nao disponivel — publicacao ignorada (modo no-op).");
            return;
        }
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, evento);
    }
}
