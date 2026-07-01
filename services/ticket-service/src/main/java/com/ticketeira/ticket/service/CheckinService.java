package com.ticketeira.ticket.service;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.common.exception.NotFoundException;
import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.client.EventResumo;
import com.ticketeira.ticket.domain.Checkin;
import com.ticketeira.ticket.domain.Ingresso;
import com.ticketeira.ticket.domain.Inscricao;
import com.ticketeira.ticket.domain.StatusIngresso;
import com.ticketeira.ticket.dto.CheckinResponse;
import com.ticketeira.ticket.repository.CheckinRepository;
import com.ticketeira.ticket.repository.IngressoRepository;
import com.ticketeira.ticket.repository.InscricaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servico de check-in (US-034).
 * Fluxo: lookup por codigoUnico -> validar ATIVO -> EventClient.getEvento -> ownership ->
 *        tx: realizarCheckin() + salvar Checkin. UNIQUE(ingresso_id) fecha duplo check-in.
 *
 * Estrategia de concorrencia:
 * - Ingresso.realizarCheckin() lanca 409 se ja UTILIZADO (caminho sequencial).
 * - UNIQUE(ingresso_id) em checkins + DataIntegrityViolationException -> 409 (caminho concorrente).
 */
@Service
public class CheckinService {

    private static final Logger log = LoggerFactory.getLogger(CheckinService.class);

    private final IngressoRepository ingressoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final CheckinRepository checkinRepository;
    private final EventClient eventClient;

    public CheckinService(IngressoRepository ingressoRepository,
                          InscricaoRepository inscricaoRepository,
                          CheckinRepository checkinRepository,
                          EventClient eventClient) {
        this.ingressoRepository = ingressoRepository;
        this.inscricaoRepository = inscricaoRepository;
        this.checkinRepository = checkinRepository;
        this.eventClient = eventClient;
    }

    /**
     * Realiza o check-in do ingresso identificado pelo codigoUnico.
     * @param codigoUnico codigo do QR do ingresso
     * @param userId      id do promotor autenticado (via X-User-Id)
     * @return CheckinResponse com ingressoId, inscricaoId, status=UTILIZADO, realizadoEm
     */
    @Transactional
    public CheckinResponse realizarCheckin(String codigoUnico, Long userId) {
        // PASSO 1 — buscar ingresso por codigoUnico
        Ingresso ingresso = ingressoRepository.findByCodigoUnico(codigoUnico)
                .orElseThrow(() -> new NotFoundException("INGRESSO_NAO_ENCONTRADO"));

        // PASSO 2 — ingresso CANCELADO = invalido para check-in (404, nao 409)
        if (ingresso.getStatus() == StatusIngresso.CANCELADO) {
            throw new NotFoundException("INGRESSO_NAO_ENCONTRADO");
        }

        // PASSO 3 — buscar inscricao para obter eventoId
        Inscricao inscricao = inscricaoRepository.findById(ingresso.getInscricaoId())
                .orElseThrow(() -> new NotFoundException("INGRESSO_NAO_ENCONTRADO"));

        // PASSO 4 — validar ownership do evento (ADR-T14)
        EventResumo evento = eventClient.getEvento(inscricao.getEventoId());
        if (!userId.equals(evento.promotorId())) {
            throw new BusinessException("CHECKIN_EVENTO_ALHEIO", 403);
        }

        // PASSO 5 — transicao de estado + criacao do checkin
        try {
            ingresso.realizarCheckin();   // lanca 409 se ja UTILIZADO/CANCELADO
            ingressoRepository.save(ingresso);
            Checkin checkin = checkinRepository.saveAndFlush(Checkin.de(ingresso.getId()));
            return CheckinResponse.from(ingresso, checkin);
        } catch (BusinessException e) {
            throw e;   // INGRESSO_JA_UTILIZADO (409)
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(ingresso_id) em checkins — duplo check-in concorrente
            log.warn("Duplo check-in concorrente bloqueado pelo UNIQUE para ingresso={}: {}",
                    ingresso.getId(), e.getMessage());
            throw new BusinessException("INGRESSO_JA_UTILIZADO", 409);
        }
    }
}
