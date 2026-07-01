package com.ticketeira.ticket.service;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.client.EventResumo;
import com.ticketeira.ticket.domain.Ingresso;
import com.ticketeira.ticket.domain.Inscricao;
import com.ticketeira.ticket.domain.StatusIngresso;
import com.ticketeira.ticket.dto.CancelamentoResponse;
import com.ticketeira.ticket.messaging.InscricaoCanceladaEvent;
import com.ticketeira.ticket.messaging.InscricaoCanceladaPublisher;
import com.ticketeira.ticket.repository.IngressoRepository;
import com.ticketeira.ticket.repository.InscricaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Servico de cancelamento voluntario de inscricao (US-035).
 *
 * Fluxo:
 *   1. Busca inscricao (404 se inexistente).
 *   2. Valida ownership: inscricao.usuarioId == userId (403 senao).
 *   3. Para PAGO: checa prazo (422 fora do prazo, PO-D2).
 *   4. TX local: cancelarPorParticipante (0 -> 409); cancela ingresso se houver.
 *   5. afterCommit: liberarVaga + (se PAGO + no prazo) publica inscricao.cancelada.
 *
 * Estrategia de concorrencia:
 *   UPDATE inscricoes SET status=CANCELADA WHERE id=? AND status IN (ATIVA, PENDENTE_PAGAMENTO)
 *   -> rowsAffected=0 -> 409 INSCRICAO_JA_CANCELADA. Serializa duplicados via row lock do Postgres.
 *
 * Nota de design: cancelar() usa TransactionTemplate (REQUIRES_NEW) para encerrar o commit
 * antes de registrar o afterCommit que chama liberarVaga/publicar. Espelha InscricaoService.
 */
@Service
public class CancelamentoInscricaoService {

    private static final Logger log = LoggerFactory.getLogger(CancelamentoInscricaoService.class);

    private final InscricaoRepository inscricaoRepository;
    private final IngressoRepository ingressoRepository;
    private final EventClient eventClient;
    private final InscricaoCanceladaPublisher inscricaoCanceladaPublisher;
    private final TransactionTemplate txTemplate;

    public CancelamentoInscricaoService(InscricaoRepository inscricaoRepository,
                                        IngressoRepository ingressoRepository,
                                        EventClient eventClient,
                                        InscricaoCanceladaPublisher inscricaoCanceladaPublisher,
                                        PlatformTransactionManager txManager) {
        this.inscricaoRepository = inscricaoRepository;
        this.ingressoRepository = ingressoRepository;
        this.eventClient = eventClient;
        this.inscricaoCanceladaPublisher = inscricaoCanceladaPublisher;
        this.txTemplate = new TransactionTemplate(txManager);
        this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Cancela a inscricao do participante.
     * @param inscricaoId id da inscricao a cancelar
     * @param userId      id do usuario autenticado (ownership check)
     * @return CancelamentoResponse com status=CANCELADA e reembolsoIniciado
     */
    public CancelamentoResponse cancelar(Long inscricaoId, Long userId) {
        // PASSO 1 — buscar inscricao
        Inscricao inscricao = inscricaoRepository.findById(inscricaoId)
                .orElseThrow(() -> new BusinessException("INSCRICAO_NAO_ENCONTRADA", 404));

        // PASSO 2 — validar ownership
        if (!inscricao.getUsuarioId().equals(userId)) {
            throw new BusinessException("CANCELAMENTO_DE_OUTRO", 403);
        }

        // PASSO 3 — buscar evento para politica de prazo e tipo
        EventResumo evento = eventClient.getEvento(inscricao.getEventoId());

        // PASSO 4 — para PAGO: verificar prazo antes de cancelar
        boolean pagoDentroDoPrazo = false;
        if ("PAGO".equals(evento.tipo())) {
            OffsetDateTime limite = evento.dataInicio().minusDays(
                    evento.prazoReembolsoDias() != null ? evento.prazoReembolsoDias() : 0);
            if (OffsetDateTime.now().isAfter(limite)) {
                throw new BusinessException("PRAZO_CANCELAMENTO_ENCERRADO", 422);
            }
            pagoDentroDoPrazo = true;
        }

        final boolean devePublicar = pagoDentroDoPrazo;
        final Long eventoId = inscricao.getEventoId();
        final Long usuarioIdFinal = inscricao.getUsuarioId();

        // PASSO 5 — TX local: cancelar inscricao + ingresso
        txTemplate.execute(txStatus -> {
            // Transicao condicional: 0 -> ja cancelada/expirada -> 409
            int rows = inscricaoRepository.cancelarPorParticipante(inscricaoId);
            if (rows == 0) {
                txStatus.setRollbackOnly();
                throw new BusinessException("INSCRICAO_JA_CANCELADA", 409);
            }

            // Cancelar ingresso ATIVO (se houver); UTILIZADO e preservado
            ingressoRepository.findByInscricaoId(inscricaoId).ifPresent(ing -> {
                if (ing.getStatus() == StatusIngresso.ATIVO) {
                    ing.cancelar();
                    ingressoRepository.save(ing);
                }
            });

            // Registrar afterCommit: liberar vaga + (opcionalmente) publicar
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                final InscricaoCanceladaEvent eventoAmqp = devePublicar
                        ? new InscricaoCanceladaEvent(
                                UUID.randomUUID(), inscricaoId, usuarioIdFinal, eventoId,
                                OffsetDateTime.now())
                        : null;

                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                liberarVagaComLog(eventoId);
                                if (devePublicar && eventoAmqp != null) {
                                    inscricaoCanceladaPublisher.publicar(eventoAmqp);
                                }
                            }
                        });
            } else {
                // Sem sincronizacao ativa (testes unitarios com TX mockada): chamar inline
                liberarVagaComLog(eventoId);
                if (devePublicar) {
                    inscricaoCanceladaPublisher.publicar(new InscricaoCanceladaEvent(
                            UUID.randomUUID(), inscricaoId, usuarioIdFinal, eventoId,
                            OffsetDateTime.now()));
                }
            }

            return null;
        });

        return new CancelamentoResponse(inscricaoId, "CANCELADA", devePublicar);
    }

    private void liberarVagaComLog(Long eventoId) {
        try {
            eventClient.liberarVaga(eventoId);
        } catch (Exception e) {
            log.error("[RECONCILIACAO] liberarVaga FALHOU para evento={}: {}. Requer reconciliacao manual.",
                    eventoId, e.getMessage());
        }
    }
}
