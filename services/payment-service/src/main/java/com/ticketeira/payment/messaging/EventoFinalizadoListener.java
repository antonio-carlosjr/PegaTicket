package com.ticketeira.payment.messaging;

import com.ticketeira.payment.domain.ProcessedEvent;
import com.ticketeira.payment.repository.PagamentoRepository;
import com.ticketeira.payment.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Consumidor de evento.finalizado (US-043 — repasse pos-evento).
 * Idempotente via processed_events (PK UUID colide na reentrega → ACK no-op).
 * Estrategia de concorrencia: 1 UPDATE condicional (WHERE status='CONFIRMADO') —
 * O(1) round-trips; row lock do Postgres serializa corrida vs. reembolso.
 * ACK no-op se 0 linhas afetadas (pagamentos ja em estado avancado — CR-S4-01).
 */
@Component
public class EventoFinalizadoListener {

    private static final Logger log = LoggerFactory.getLogger(EventoFinalizadoListener.class);

    private final PagamentoRepository pagamentoRepository;
    private final ProcessedEventRepository processedEventRepository;

    public EventoFinalizadoListener(PagamentoRepository pagamentoRepository,
                                    ProcessedEventRepository processedEventRepository) {
        this.pagamentoRepository = pagamentoRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE_EVENTO_FINALIZADO)
    @Transactional
    public void onEventoFinalizado(EventoFinalizadoEvent evento) {
        log.info("Recebido evento.finalizado: eventId={}, eventoId={}", evento.eventId(), evento.eventoId());

        // Idempotencia: INSERT processed_events; PK colide na reentrega → no-op
        try {
            processedEventRepository.saveAndFlush(
                    ProcessedEvent.of(evento.eventId(), RabbitConfig.QUEUE_EVENTO_FINALIZADO));
        } catch (DataIntegrityViolationException e) {
            log.info("evento.finalizado ja processado (reentrega): eventId={}", evento.eventId());
            return;
        }

        int afetados = pagamentoRepository.repassarConfirmadosDoEvento(
                evento.eventoId(), OffsetDateTime.now());

        log.info("Repasse aplicado a {} pagamento(s) do eventoId={}", afetados, evento.eventoId());
        // 0 afetados = pagamentos ja em estado avancado ou inexistentes → ACK normal (sem poison message)
    }
}
