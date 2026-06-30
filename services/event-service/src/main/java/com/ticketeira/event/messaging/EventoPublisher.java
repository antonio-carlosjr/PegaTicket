package com.ticketeira.event.messaging;

import com.ticketeira.event.domain.Evento;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Publica eventos de dominio do event-service no broker RabbitMQ.
 *
 * CRITICO: toda publicacao e registrada como afterCommit (TransactionSynchronization).
 * Nenhuma mensagem vai ao broker se a transacao fizer rollback (ADR-T11).
 *
 * O eventId UUID e gerado na origem (aqui) e serve de chave de idempotencia
 * para os consumidores (processed_events no payment e ticket).
 */
@Component
public class EventoPublisher {

    private final RabbitTemplate rabbitTemplate;

    public EventoPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Registra publicacao de evento.finalizado para apos o commit da transacao atual.
     * Chamado por EventService.encerrar() dentro da @Transactional.
     */
    public void publicarEventoFinalizado(Evento evento) {
        EventoFinalizadoEvent payload = new EventoFinalizadoEvent(
                UUID.randomUUID(),
                evento.getId(),
                evento.getPromotorId(),
                OffsetDateTime.now()
        );
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbitTemplate.convertAndSend(
                        RabbitConfig.EXCHANGE_EVENTS,
                        RabbitConfig.RK_EVENTO_FINALIZADO,
                        payload
                );
            }
        });
    }

    /**
     * Registra publicacao de evento.cancelado para apos o commit da transacao atual.
     * Chamado por EventService.cancelar() dentro da @Transactional.
     * Fan-out: payment (evento.cancelado) e ticket (evento.cancelado.ticket) recebem
     * cada um sua copia via bindings distintos na mesma routing key.
     */
    public void publicarEventoCancelado(Evento evento) {
        EventoCanceladoEvent payload = new EventoCanceladoEvent(
                UUID.randomUUID(),
                evento.getId(),
                evento.getPromotorId(),
                OffsetDateTime.now()
        );
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbitTemplate.convertAndSend(
                        RabbitConfig.EXCHANGE_EVENTS,
                        RabbitConfig.RK_EVENTO_CANCELADO,
                        payload
                );
            }
        });
    }
}
