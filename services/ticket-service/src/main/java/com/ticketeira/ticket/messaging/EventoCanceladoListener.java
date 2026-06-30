package com.ticketeira.ticket.messaging;

import com.ticketeira.ticket.domain.ProcessedEvent;
import com.ticketeira.ticket.repository.IngressoRepository;
import com.ticketeira.ticket.repository.InscricaoRepository;
import com.ticketeira.ticket.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumidor da fila dedicada evento.cancelado.ticket (fan-out do event-service).
 * Efeito (mesma tx): INSERT processed_events + cancelar ingressos ATIVO + cancelar inscricoes.
 * Idempotente via processed_events (PK UUID = eventId). Nunca lanca apos o catch — CR-S4-01.
 * Re-entrega com mesmo eventId: PK colide -> tx desfaz -> ACK no-op.
 * Espelha PagamentoAprovadoListener (gabarito de consumidor idempotente do S4).
 */
@Component
@Profile("!test")
public class EventoCanceladoListener {

    private static final Logger log = LoggerFactory.getLogger(EventoCanceladoListener.class);
    private static final String ROUTING_KEY = "evento.cancelado.ticket";

    private final InscricaoRepository inscricaoRepository;
    private final IngressoRepository ingressoRepository;
    private final ProcessedEventRepository processedEventRepository;

    public EventoCanceladoListener(InscricaoRepository inscricaoRepository,
                                   IngressoRepository ingressoRepository,
                                   ProcessedEventRepository processedEventRepository) {
        this.inscricaoRepository = inscricaoRepository;
        this.ingressoRepository = ingressoRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @RabbitListener(queues = "evento.cancelado.ticket")
    @Transactional
    public void consumir(EventoCanceladoEvent evento) {
        log.debug("Recebendo evento.cancelado: eventId={} eventoId={}",
                evento.eventId(), evento.eventoId());

        // Idempotencia: INSERT processed_events na mesma tx — PK colide na re-entrega
        try {
            processedEventRepository.saveAndFlush(
                    ProcessedEvent.of(evento.eventId(), ROUTING_KEY));
        } catch (DataIntegrityViolationException e) {
            log.info("evento.cancelado ja processado (idempotente): eventId={}", evento.eventId());
            return;
        }

        // Cancelar ingressos ATIVO antes das inscricoes (subquery por evento_id — sem N+1)
        int ingressosCancelados = ingressoRepository.cancelarIngressosDoEvento(evento.eventoId());

        // Cancelar inscricoes ATIVA|PENDENTE_PAGAMENTO (UPDATE condicional em massa)
        int inscricoesCanceladas = inscricaoRepository.cancelarInscricoesDoEvento(evento.eventoId());

        log.info("Cancelamento do evento {}: {} inscricoes e {} ingressos cancelados.",
                evento.eventoId(), inscricoesCanceladas, ingressosCancelados);
    }
}
