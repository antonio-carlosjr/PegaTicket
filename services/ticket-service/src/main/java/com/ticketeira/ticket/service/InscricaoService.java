package com.ticketeira.ticket.service;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.client.EventResumo;
import com.ticketeira.ticket.domain.Ingresso;
import com.ticketeira.ticket.domain.Inscricao;
import com.ticketeira.ticket.dto.InscricaoHistoricoResponse;
import com.ticketeira.ticket.dto.InscricaoResponse;
import com.ticketeira.ticket.dto.MeuIngressoResponse;
import com.ticketeira.ticket.repository.IngressoRepository;
import com.ticketeira.ticket.repository.InscricaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Orquestra a mini-saga sincrona cross-service (ticket→event).
 * Ordem definitiva: validar evento → pre-check → reservar vaga → tx local → compensar se falhou.
 *
 * Estrategia de concorrencia:
 *   - Overbooking: UPDATE atomico no event-service (rowsAffected) — ADR-T07
 *   - Dupla inscricao: UNIQUE(usuario_id,evento_id) + captura DataIntegrityViolationException — ADR-T07
 *   - Ingresso unico: UNIQUE(inscricao_id) na mesma tx local
 *
 * Nota de design: inscrever() NAO e @Transactional — a tx local (passo 3) e criada via
 * TransactionTemplate para permitir captura de DataIntegrityViolationException FORA
 * da transacao (com Spring AOP, excecoes dentro de @Transactional sao re-lancadas apos
 * rollback; o DataIntegrityViolationException so e visivel apos o flush/commit, entao
 * precisamos que a tx encerre antes do catch da compensacao).
 */
@Service
public class InscricaoService {

    private static final Logger log = LoggerFactory.getLogger(InscricaoService.class);

    private final InscricaoRepository inscricaoRepository;
    private final IngressoRepository ingressoRepository;
    private final EventClient eventClient;
    private final TransactionTemplate txTemplate;

    public InscricaoService(InscricaoRepository inscricaoRepository,
                            IngressoRepository ingressoRepository,
                            EventClient eventClient,
                            PlatformTransactionManager txManager) {
        this.inscricaoRepository = inscricaoRepository;
        this.ingressoRepository = ingressoRepository;
        this.eventClient = eventClient;
        this.txTemplate = new TransactionTemplate(txManager);
        this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Mini-saga: inscreve usuario em evento GRATUITO publicado.
     *
     * PASSO 1: Valida evento (existe, PUBLICADO, GRATUITO) — sem efeito se falhar.
     * PASSO 1.5: Pre-check de duplicidade (otimizacao, nao defesa contra corrida).
     * PASSO 2: Reserva vaga (decremento atomico) — nao-idempotente, nao re-tentar.
     * PASSO 3: TX local (REQUIRES_NEW via TransactionTemplate) — cria Inscricao + Ingresso.
     *          Se falhar: COMPENSA via liberarVaga; se compensacao falhar: loga ERROR.
     * PASSO 4: Retorna 201 InscricaoResponse.
     */
    public InscricaoResponse inscrever(Long eventoId, Long usuarioId) {
        // PASSO 1 — validar evento
        EventResumo evento = eventClient.getEvento(eventoId);

        if (!"PUBLICADO".equals(evento.status())) {
            throw new BusinessException("EVENTO_NAO_PUBLICADO", 422);
        }
        if (!"GRATUITO".equals(evento.tipo())) {
            throw new BusinessException("EVENTO_PAGO_NAO_SUPORTADO", 422);
        }

        // PASSO 1.5 — pre-check de duplicidade (evita gastar vaga no caso comum)
        if (inscricaoRepository.existsByUsuarioIdAndEventoId(usuarioId, eventoId)) {
            throw new BusinessException("JA_INSCRITO", 409);
        }

        // PASSO 2 — reservar vaga (decremento atomico; nao-idempotente: nao re-tentar)
        eventClient.reservarVaga(eventoId);

        // PASSO 3 — tx local atomica (REQUIRES_NEW): INSERT inscricao + INSERT ingresso
        try {
            return txTemplate.execute(status -> {
                Inscricao inscricao = inscricaoRepository.save(Inscricao.criar(usuarioId, eventoId));
                Ingresso ingresso = ingressoRepository.save(Ingresso.emitir(inscricao.getId()));
                return InscricaoResponse.from(inscricao, ingresso);
            });
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(usuario_id,evento_id): corrida — dupla inscricao paralela passou no pre-check
            compensar(eventoId, usuarioId, e);
            throw new BusinessException("JA_INSCRITO", 409);
        } catch (Exception e) {
            compensar(eventoId, usuarioId, e);
            throw new BusinessException("EVENTO_INDISPONIVEL", 503);
        }
    }

    private void compensar(Long eventoId, Long usuarioId, Exception causa) {
        log.warn("Tx local falhou para usuario={} evento={}: {}. Iniciando compensacao (liberar-vaga).",
                usuarioId, eventoId, causa.getMessage());
        try {
            eventClient.liberarVaga(eventoId);
        } catch (Exception compensacaoEx) {
            log.error("[RECONCILIACAO] Compensacao de liberar-vaga FALHOU para evento={} usuario={}: {}. " +
                      "Vaga pode estar presa — requer reconciliacao manual.",
                    eventoId, usuarioId, compensacaoEx.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<MeuIngressoResponse> meusIngressos(Long usuarioId) {
        return ingressoRepository.findIngressoComInscricaoByUsuarioId(usuarioId)
                .stream()
                .map(row -> MeuIngressoResponse.from((Ingresso) row[0], (Inscricao) row[1]))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<InscricaoHistoricoResponse> historicoInscricoes(Long usuarioId, Pageable pageable) {
        return inscricaoRepository.findByUsuarioIdOrderByInscritoEmDesc(usuarioId, pageable)
                .map(InscricaoHistoricoResponse::from);
    }
}
