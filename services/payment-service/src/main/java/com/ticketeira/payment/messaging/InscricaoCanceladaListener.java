package com.ticketeira.payment.messaging;

import com.ticketeira.payment.domain.ProcessedEvent;
import com.ticketeira.payment.repository.ProcessedEventRepository;
import com.ticketeira.payment.service.PagamentoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumidor de inscricao.cancelada (US-035 / US-042 individual — reembolso individual).
 * Idempotente via processed_events (PK UUID colide na reentrega -> ACK no-op).
 * Estrategia de concorrencia: reembolsarPorInscricao usa findByInscricaoIdForUpdate
 * (PESSIMISTIC_WRITE), que serializa corrida vs. reembolso em massa (CR-S4-01).
 * Espelha EventoCanceladoListener (gabarito 5A).
 */
@Component
public class InscricaoCanceladaListener {

    private static final Logger log = LoggerFactory.getLogger(InscricaoCanceladaListener.class);
    private static final String MOTIVO_CANCELAMENTO_PARTICIPANTE = "CANCELAMENTO_PARTICIPANTE";

    private final PagamentoService pagamentoService;
    private final ProcessedEventRepository processedEventRepository;

    public InscricaoCanceladaListener(PagamentoService pagamentoService,
                                      ProcessedEventRepository processedEventRepository) {
        this.pagamentoService = pagamentoService;
        this.processedEventRepository = processedEventRepository;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE_INSCRICAO_CANCELADA)
    @Transactional
    public void onInscricaoCancelada(InscricaoCanceladaEvent evento) {
        log.info("Recebido inscricao.cancelada: eventId={}, inscricaoId={}", evento.eventId(), evento.inscricaoId());

        // Idempotencia: INSERT processed_events; PK colide na reentrega -> no-op
        try {
            processedEventRepository.saveAndFlush(
                    ProcessedEvent.of(evento.eventId(), RabbitConfig.QUEUE_INSCRICAO_CANCELADA));
        } catch (DataIntegrityViolationException e) {
            log.info("inscricao.cancelada ja processado (reentrega): eventId={}", evento.eventId());
            return;
        }

        // Reembolso individual via service (participa da mesma tx — beans distintos, proxy ok)
        // ACK no-op se ausente (gratuito/defesa) ou nao-CONFIRMADO (CR-S4-01 — nunca poison message)
        pagamentoService.reembolsarPorInscricao(evento.inscricaoId(), MOTIVO_CANCELAMENTO_PARTICIPANTE);
    }
}
