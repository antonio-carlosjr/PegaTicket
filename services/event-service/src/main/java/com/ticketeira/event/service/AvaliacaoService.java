package com.ticketeira.event.service;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.common.exception.NotFoundException;
import com.ticketeira.event.client.TicketClient;
import com.ticketeira.event.domain.Avaliacao;
import com.ticketeira.event.domain.StatusEvento;
import com.ticketeira.event.repository.AvaliacaoRepository;
import com.ticketeira.event.repository.EventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servico de avaliacao de eventos (US-024).
 * Pre-filtro: evento.status != REALIZADO -> 403 sem chamar o ticket-service.
 * Elegibilidade: ticketClient.participou(...) == false -> 403.
 * Duplicata: UNIQUE(evento,usuario) -> DataIntegrityViolation -> 409 AVALIACAO_DUPLICADA.
 */
@Service
public class AvaliacaoService {

    private final EventRepository eventRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final TicketClient ticketClient;

    public AvaliacaoService(EventRepository eventRepository,
                            AvaliacaoRepository avaliacaoRepository,
                            TicketClient ticketClient) {
        this.eventRepository = eventRepository;
        this.avaliacaoRepository = avaliacaoRepository;
        this.ticketClient = ticketClient;
    }

    @Transactional
    public Avaliacao avaliar(Long eventoId, Long usuarioId, Integer nota, String comentario) {
        var evento = eventRepository.findById(eventoId)
                .orElseThrow(() -> new NotFoundException("Evento nao encontrado."));

        // Pre-filtro local: evita round-trip para o ticket quando o evento nao esta REALIZADO
        if (evento.getStatus() != StatusEvento.REALIZADO) {
            throw new BusinessException("AVALIACAO_NAO_ELEGIVEL", 403);
        }

        // Elegibilidade via canal interno (ticket e a fonte de verdade sobre participacao)
        boolean participou;
        try {
            participou = ticketClient.participou(usuarioId, eventoId);
        } catch (BusinessException ex) {
            // Propaga TICKET_INDISPONIVEL (503) ou qualquer erro tipado do client — nunca 500
            throw ex;
        } catch (Exception ex) {
            // Runtime inesperado do client -> falha fechada
            throw new BusinessException("TICKET_INDISPONIVEL", 503);
        }

        if (!participou) {
            throw new BusinessException("AVALIACAO_NAO_ELEGIVEL", 403);
        }

        try {
            return avaliacaoRepository.saveAndFlush(
                    Avaliacao.criar(eventoId, usuarioId, nota, comentario));
        } catch (DataIntegrityViolationException ex) {
            // UNIQUE(evento_id, usuario_id) -> 2a avaliacao -> 409
            throw new BusinessException("AVALIACAO_DUPLICADA", 409);
        }
    }
}
