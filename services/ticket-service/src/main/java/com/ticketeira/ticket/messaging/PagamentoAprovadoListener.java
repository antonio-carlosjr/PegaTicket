package com.ticketeira.ticket.messaging;

import com.ticketeira.common.exception.NotFoundException;
import com.ticketeira.ticket.domain.Ingresso;
import com.ticketeira.ticket.domain.Inscricao;
import com.ticketeira.ticket.domain.ProcessedEvent;
import com.ticketeira.ticket.domain.StatusInscricao;
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
 * Consumidor de pagamento.aprovado (payment-service -> ticket-service).
 * Idempotente via processed_events (PK UUID = eventId do payload).
 * Efeito: dentro da MESMA TX -> INSERT processed_events + emitir Ingresso + ativar Inscricao.
 * Re-entrega com mesmo eventId: PK colide -> tx desfaz -> ACK (no-op).
 * Falha nao tratada: sem ACK -> RabbitMQ re-entrega; apos limite -> DLQ.
 * Perfil "test" exclui RabbitAutoConfiguration — listener so ativo com broker real.
 */
@Component
@Profile("!test")   // excluido no perfil test (sem broker real)
public class PagamentoAprovadoListener {

    private static final Logger log = LoggerFactory.getLogger(PagamentoAprovadoListener.class);

    private final InscricaoRepository inscricaoRepository;
    private final IngressoRepository ingressoRepository;
    private final ProcessedEventRepository processedEventRepository;

    public PagamentoAprovadoListener(InscricaoRepository inscricaoRepository,
                                     IngressoRepository ingressoRepository,
                                     ProcessedEventRepository processedEventRepository) {
        this.inscricaoRepository = inscricaoRepository;
        this.ingressoRepository = ingressoRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @RabbitListener(queues = "pagamento.aprovado")
    @Transactional
    public void consumir(PagamentoAprovadoEvent evento) {
        log.debug("Recebendo pagamento.aprovado: eventId={} inscricaoId={}",
                evento.eventId(), evento.inscricaoId());

        // Idempotencia: INSERT processed_events na mesma tx — PK colide na re-entrega
        try {
            processedEventRepository.saveAndFlush(
                    ProcessedEvent.of(evento.eventId(), "pagamento.aprovado"));
        } catch (DataIntegrityViolationException e) {
            // Segunda entrega com mesmo eventId: PK ja existe — no-op, ACK
            log.info("pagamento.aprovado ja processado (idempotente): eventId={}", evento.eventId());
            return;
        }

        // Emitir ingresso e ativar inscricao na mesma tx
        Inscricao inscricao = inscricaoRepository.findById(evento.inscricaoId())
                .orElseThrow(() -> new NotFoundException("INSCRICAO_NAO_ENCONTRADA"));

        // Guard de estado (Risco R6): se a inscricao nao esta mais PENDENTE_PAGAMENTO
        // (ex.: o ExpiracaoReservaJob ja a expirou entre a confirmacao e esta entrega, ou ela
        // ja foi ativada por uma entrega anterior com outro eventId), NAO emitir ingresso.
        // A confirmacao tardia de uma EXPIRADA e um no-op aceitavel (ADR-T10/R6): ACK + commit
        // do processed_events. Sem este guard, inscricao.ativar() lancaria, a tx faria rollback,
        // o processed_events seria desfeito e a mensagem entraria em loop de re-entrega -> DLQ.
        if (inscricao.getStatus() != StatusInscricao.PENDENTE_PAGAMENTO) {
            log.warn("pagamento.aprovado para inscricao em estado {} (esperado PENDENTE_PAGAMENTO): "
                    + "inscricaoId={} — sem emissao de ingresso (no-op idempotente, ACK).",
                    inscricao.getStatus(), inscricao.getId());
            return;
        }

        inscricao.ativar();
        inscricaoRepository.save(inscricao);

        Ingresso ingresso = ingressoRepository.save(Ingresso.emitir(inscricao.getId()));

        log.info("Ingresso emitido: inscricaoId={} codigoUnico={}",
                inscricao.getId(), ingresso.getCodigoUnico());
    }
}
