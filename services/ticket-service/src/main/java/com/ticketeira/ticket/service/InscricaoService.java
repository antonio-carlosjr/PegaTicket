package com.ticketeira.ticket.service;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.client.EventResumo;
import com.ticketeira.ticket.domain.Ingresso;
import com.ticketeira.ticket.domain.Inscricao;
import com.ticketeira.ticket.dto.InscricaoHistoricoResponse;
import com.ticketeira.ticket.dto.InscricaoResponse;
import com.ticketeira.ticket.dto.MeuIngressoResponse;
import com.ticketeira.ticket.dto.PagamentoPendenteResponse;
import com.ticketeira.ticket.messaging.PedidoCriadoEvent;
import com.ticketeira.ticket.messaging.PedidoCriadoPublisher;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Orquestra a saga de inscricao (GRATUITO e PAGO).
 *
 * GRATUITO (S3, inalterado): valida PUBLICADO+GRATUITO → pre-check → reservarVaga → tx local
 *   cria Inscricao(ATIVA) + Ingresso imediato. Sem mensageria.
 *
 * PAGO (S4): valida PUBLICADO+PAGO → pre-check → reservarVaga → tx local cria
 *   Inscricao(PENDENTE_PAGAMENTO) SEM ingresso → afterCommit publica pedido.criado.
 *   Ingresso e emitido pelo PagamentoAprovadoListener ao receber pagamento.aprovado.
 *
 * Estrategia de concorrencia:
 *   - Overbooking: UPDATE atomico no event-service (ADR-T07) — rowsAffected = 0 → 409
 *   - Dupla inscricao: UNIQUE(usuario_id,evento_id) + DataIntegrityViolationException → 409
 *   - Compensacao: liberarVaga() em falha da tx local
 *
 * Nota de design: inscrever() NAO e @Transactional — a tx local e REQUIRES_NEW via
 * TransactionTemplate para encerrar antes do catch de compensacao e para registrar o
 * afterCommit (TransactionSynchronizationManager) que publica pedido.criado.
 */
@Service
public class InscricaoService {

    private static final Logger log = LoggerFactory.getLogger(InscricaoService.class);

    private final InscricaoRepository inscricaoRepository;
    private final IngressoRepository ingressoRepository;
    private final EventClient eventClient;
    private final PedidoCriadoPublisher pedidoCriadoPublisher;
    private final TransactionTemplate txTemplate;

    public InscricaoService(InscricaoRepository inscricaoRepository,
                            IngressoRepository ingressoRepository,
                            EventClient eventClient,
                            PedidoCriadoPublisher pedidoCriadoPublisher,
                            PlatformTransactionManager txManager) {
        this.inscricaoRepository = inscricaoRepository;
        this.ingressoRepository = ingressoRepository;
        this.eventClient = eventClient;
        this.pedidoCriadoPublisher = pedidoCriadoPublisher;
        this.txTemplate = new TransactionTemplate(txManager);
        this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Inscreve o usuario num evento PUBLICADO (GRATUITO ou PAGO).
     * Ramo GRATUITO: emite ingresso imediato (S3, inalterado).
     * Ramo PAGO: cria PENDENTE_PAGAMENTO, publica pedido.criado em afterCommit.
     */
    public InscricaoResponse inscrever(Long eventoId, Long usuarioId) {
        // PASSO 1 — validar evento (PUBLICADO; tipo determina o ramo)
        EventResumo evento = eventClient.getEvento(eventoId);

        if (!"PUBLICADO".equals(evento.status())) {
            throw new BusinessException("EVENTO_NAO_PUBLICADO", 422);
        }

        // PASSO 1.5 — pre-check de duplicidade (otimizacao; defesa final e o UNIQUE)
        if (inscricaoRepository.existsByUsuarioIdAndEventoId(usuarioId, eventoId)) {
            throw new BusinessException("JA_INSCRITO", 409);
        }

        // PASSO 2 — reservar vaga (decremento atomico; nao-idempotente, nao re-tentar)
        eventClient.reservarVaga(eventoId);

        // PASSO 3 — bifurca por tipo
        if ("PAGO".equals(evento.tipo())) {
            return inscreverPago(evento, usuarioId);
        } else {
            return inscreverGratuito(eventoId, usuarioId);
        }
    }

    /**
     * Ramo GRATUITO (S3): TX local cria Inscricao(ATIVA) + Ingresso imediato.
     * Sem mensageria.
     */
    private InscricaoResponse inscreverGratuito(Long eventoId, Long usuarioId) {
        try {
            return txTemplate.execute(status -> {
                Inscricao inscricao = inscricaoRepository.save(Inscricao.criar(usuarioId, eventoId));
                Ingresso ingresso = ingressoRepository.save(Ingresso.emitir(inscricao.getId()));
                return InscricaoResponse.from(inscricao, ingresso);
            });
        } catch (DataIntegrityViolationException e) {
            compensar(eventoId, usuarioId, e);
            throw new BusinessException("JA_INSCRITO", 409);
        } catch (Exception e) {
            compensar(eventoId, usuarioId, e);
            throw new BusinessException("EVENTO_INDISPONIVEL", 503);
        }
    }

    /**
     * Ramo PAGO (S4): TX local cria Inscricao(PENDENTE_PAGAMENTO) sem ingresso.
     * afterCommit publica pedido.criado com preco e promotorId do evento.
     */
    private InscricaoResponse inscreverPago(EventResumo evento, Long usuarioId) {
        try {
            return txTemplate.execute(txStatus -> {
                Inscricao inscricao = inscricaoRepository.save(
                        Inscricao.pendentePagamento(usuarioId, evento.id()));

                // Monta o evento ANTES do afterCommit para capturar o id da inscricao salva
                PedidoCriadoEvent pedido = new PedidoCriadoEvent(
                        UUID.randomUUID(),
                        inscricao.getId(),
                        usuarioId,
                        evento.id(),
                        evento.preco(),
                        evento.promotorId(),
                        OffsetDateTime.now());

                // Publica somente apos o commit — se a tx fizer rollback, nao sai nada.
                // Se nao ha gerenciamento de sincronizacao ativo (ex.: testes unitarios com TX mockada),
                // publica diretamente (a tx real nao existe para fazer rollback de qualquer forma).
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(
                            new TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                    pedidoCriadoPublisher.publicar(pedido);
                                }
                            });
                } else {
                    pedidoCriadoPublisher.publicar(pedido);
                }

                PagamentoPendenteResponse pagamento = new PagamentoPendenteResponse(
                        inscricao.getId(),
                        evento.preco(),
                        "AGUARDANDO");

                return InscricaoResponse.fromPago(inscricao, pagamento);
            });
        } catch (DataIntegrityViolationException e) {
            compensar(evento.id(), usuarioId, e);
            throw new BusinessException("JA_INSCRITO", 409);
        } catch (Exception e) {
            compensar(evento.id(), usuarioId, e);
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
