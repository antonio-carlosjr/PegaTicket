package com.ticketeira.payment.messaging;

import com.ticketeira.payment.domain.ProcessedEvent;
import com.ticketeira.payment.domain.Reembolso;
import com.ticketeira.payment.repository.PagamentoRepository;
import com.ticketeira.payment.repository.ProcessedEventRepository;
import com.ticketeira.payment.repository.ReembolsoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumidor de evento.cancelado (US-042 — reembolso em massa por evento cancelado).
 * Idempotente via processed_events (PK UUID colide na reentrega → ACK no-op).
 * Estrategia de concorrencia: SELECT...FOR UPDATE carrega CONFIRMADO com lock de linha;
 * loop insere 1 Reembolso por pagamento (necessario pois e 1 registro por linha).
 * Row lock serializa corrida vs. repasse: so um dos dois encontra status='CONFIRMADO'.
 * ACK no-op se nenhum pagamento casar (CR-S4-01 — nunca poison message).
 */
@Component
public class EventoCanceladoListener {

    private static final Logger log = LoggerFactory.getLogger(EventoCanceladoListener.class);
    private static final String MOTIVO_EVENTO_CANCELADO = "EVENTO_CANCELADO";

    private final PagamentoRepository pagamentoRepository;
    private final ReembolsoRepository reembolsoRepository;
    private final ProcessedEventRepository processedEventRepository;

    public EventoCanceladoListener(PagamentoRepository pagamentoRepository,
                                   ReembolsoRepository reembolsoRepository,
                                   ProcessedEventRepository processedEventRepository) {
        this.pagamentoRepository = pagamentoRepository;
        this.reembolsoRepository = reembolsoRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE_EVENTO_CANCELADO)
    @Transactional
    public void onEventoCancelado(EventoCanceladoEvent evento) {
        log.info("Recebido evento.cancelado: eventId={}, eventoId={}", evento.eventId(), evento.eventoId());

        // Idempotencia: INSERT processed_events; PK colide na reentrega → no-op
        try {
            processedEventRepository.saveAndFlush(
                    ProcessedEvent.of(evento.eventId(), RabbitConfig.QUEUE_EVENTO_CANCELADO));
        } catch (DataIntegrityViolationException e) {
            log.info("evento.cancelado ja processado (reentrega): eventId={}", evento.eventId());
            return;
        }

        // Carrega CONFIRMADO com lock pessimista (serializa corrida vs. repasse)
        var confirmados = pagamentoRepository.findConfirmadosDoEventoForUpdate(evento.eventoId());

        if (confirmados.isEmpty()) {
            log.info("Nenhum pagamento CONFIRMADO para o eventoId={} — ACK no-op", evento.eventoId());
            return;
        }

        for (var pagamento : confirmados) {
            boolean transicionou = pagamento.reembolsar();
            if (transicionou) {
                pagamentoRepository.save(pagamento);
                Reembolso reembolso = Reembolso.criar(
                        pagamento.getId(),
                        pagamento.getUsuarioId(),
                        pagamento.getValorBruto(),
                        MOTIVO_EVENTO_CANCELADO
                );
                reembolsoRepository.save(reembolso);
            }
        }

        log.info("Reembolso aplicado a {} pagamento(s) do eventoId={}", confirmados.size(), evento.eventoId());
    }
}
