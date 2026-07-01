package com.ticketeira.payment.service;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.common.exception.NotFoundException;
import com.ticketeira.payment.domain.ConfiguracaoPlataforma;
import com.ticketeira.payment.domain.Pagamento;
import com.ticketeira.payment.domain.ProcessedEvent;
import com.ticketeira.payment.domain.Reembolso;
import com.ticketeira.payment.domain.StatusPagamento;
import com.ticketeira.payment.dto.PagamentoResponse;
import com.ticketeira.payment.messaging.PagamentoAprovadoPublisher;
import com.ticketeira.payment.messaging.PedidoCriadoEvent;
import com.ticketeira.payment.repository.ConfiguracaoPlataformaRepository;
import com.ticketeira.payment.repository.PagamentoRepository;
import com.ticketeira.payment.repository.ProcessedEventRepository;
import com.ticketeira.payment.repository.ReembolsoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PagamentoService {

    private static final Logger log = LoggerFactory.getLogger(PagamentoService.class);

    private final PagamentoRepository pagamentoRepository;
    private final ConfiguracaoPlataformaRepository configuracaoRepository;
    private final GatewaySimulado gatewaySimulado;
    private final PagamentoAprovadoPublisher publisher;
    private final PlatformTransactionManager txManager;
    private final ReembolsoRepository reembolsoRepository;

    // Injetado separado para criarPendente (que usa @Transactional declarativo)
    private ProcessedEventRepository processedEventRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public PagamentoService(PagamentoRepository pagamentoRepository,
                            ConfiguracaoPlataformaRepository configuracaoRepository,
                            GatewaySimulado gatewaySimulado,
                            PagamentoAprovadoPublisher publisher,
                            PlatformTransactionManager txManager,
                            ReembolsoRepository reembolsoRepository) {
        this.pagamentoRepository = pagamentoRepository;
        this.configuracaoRepository = configuracaoRepository;
        this.gatewaySimulado = gatewaySimulado;
        this.publisher = publisher;
        this.txManager = txManager;
        this.reembolsoRepository = reembolsoRepository;
    }

    /**
     * Construtor de compatibilidade para testes unitarios (AfterCommitRollbackTest) que
     * instanciam o service diretamente sem injecao de ReembolsoRepository.
     * O reembolsarPorInscricao nao e chamado nesses testes; reembolsoRepository fica null.
     */
    public PagamentoService(PagamentoRepository pagamentoRepository,
                            ConfiguracaoPlataformaRepository configuracaoRepository,
                            GatewaySimulado gatewaySimulado,
                            PagamentoAprovadoPublisher publisher,
                            PlatformTransactionManager txManager) {
        this(pagamentoRepository, configuracaoRepository, gatewaySimulado, publisher, txManager, null);
    }

    // Injecao por setter para compatibilidade com o construtor dos testes unitarios
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setProcessedEventRepository(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    /**
     * Cria um pagamento PENDENTE a partir do evento pedido.criado.
     * Idempotente via processed_events (INSERT na mesma tx; PK duplicada = no-op).
     */
    @Transactional
    public void criarPendente(PedidoCriadoEvent evento) {
        // Idempotencia: tenta inserir o processed_event; se a PK ja existir, e reentrega -> ignora
        try {
            if (processedEventRepository != null) {
                processedEventRepository.saveAndFlush(
                        ProcessedEvent.of(evento.eventId(), "pedido.criado"));
            }
        } catch (DataIntegrityViolationException e) {
            log.info("Evento pedido.criado ja processado: eventId={}", evento.eventId());
            return;
        }

        // Evita duplicidade por inscricaoId (UNIQUE constraint como rede final)
        if (pagamentoRepository.findByInscricaoId(evento.inscricaoId()).isPresent()) {
            log.warn("Pagamento ja existe para inscricaoId={}", evento.inscricaoId());
            return;
        }

        BigDecimal taxaPercentual = obterTaxaVigente();
        Pagamento pagamento = Pagamento.pendente(
                evento.inscricaoId(),
                evento.usuarioId(),
                evento.eventoId(),
                evento.promotorId(),
                evento.valor(),
                taxaPercentual
        );
        pagamentoRepository.save(pagamento);
        log.info("Pagamento PENDENTE criado: inscricaoId={}, eventoId={}", evento.inscricaoId(), evento.eventoId());
    }

    /**
     * Confirma o pagamento via gateway simulado.
     * Idempotente: se ja CONFIRMADO retorna o estado atual sem re-publicar.
     * Usa transacao programatica para garantir afterCommit correto.
     */
    public PagamentoResponse confirmar(Long inscricaoId, Long usuarioId) {
        return confirmarInterno(inscricaoId, usuarioId, false);
    }

    /**
     * Variante de confirmar que forca o gateway a recusar — usada no teste B2.f.
     */
    public PagamentoResponse confirmarComGatewayRecusando(Long inscricaoId, Long usuarioId) {
        return confirmarInterno(inscricaoId, usuarioId, true);
    }

    private PagamentoResponse confirmarInterno(Long inscricaoId, Long usuarioId, boolean forcarRecusa) {
        var txDef = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRED);
        var txStatus = txManager.getTransaction(txDef);

        try {
            Pagamento pagamento = pagamentoRepository.findByInscricaoIdForUpdate(inscricaoId)
                    .orElseThrow(() -> new NotFoundException("PAGAMENTO_NAO_ENCONTRADO"));

            // Verificacao de ownership
            if (!pagamento.getUsuarioId().equals(usuarioId)) {
                throw new BusinessException("PAGAMENTO_DE_OUTRO_USUARIO", 403);
            }

            // Idempotencia: ja confirmado -> no-op
            if (pagamento.getStatus() == StatusPagamento.CONFIRMADO) {
                txManager.commit(txStatus);
                return PagamentoResponse.from(pagamento);
            }

            // Chama o gateway
            String gatewayPaymentId = forcarRecusa
                    ? gatewaySimulado.aprovarRecusando(pagamento.getValorBruto())
                    : gatewaySimulado.aprovar(pagamento.getValorBruto());

            boolean transicionou = pagamento.confirmar(gatewayPaymentId);
            Pagamento salvo = pagamentoRepository.save(pagamento);

            if (transicionou) {
                // Publicacao afterCommit: so se houve transicao PENDENTE -> CONFIRMADO
                publisher.publicarAposCommit(
                        salvo.getId(),
                        salvo.getInscricaoId(),
                        salvo.getUsuarioId(),
                        salvo.getEventoId()
                );
            }

            txManager.commit(txStatus);
            return PagamentoResponse.from(salvo);

        } catch (Exception e) {
            if (!txStatus.isCompleted()) {
                txManager.rollback(txStatus);
            }
            throw e;
        }
    }

    /**
     * Lista pagamentos do usuario autenticado (mais recente primeiro).
     */
    @Transactional(readOnly = true)
    public List<PagamentoResponse> listarPorUsuario(Long usuarioId) {
        return pagamentoRepository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId)
                .stream()
                .map(PagamentoResponse::from)
                .toList();
    }

    /**
     * Retorna o pagamento de uma inscricao especifica (ownership validado no controller).
     */
    @Transactional(readOnly = true)
    public PagamentoResponse buscarPorInscricao(Long inscricaoId, Long usuarioId) {
        Pagamento pagamento = pagamentoRepository.findByInscricaoId(inscricaoId)
                .orElseThrow(() -> new NotFoundException("PAGAMENTO_NAO_ENCONTRADO"));

        if (!pagamento.getUsuarioId().equals(usuarioId)) {
            throw new BusinessException("PAGAMENTO_DE_OUTRO_USUARIO", 403);
        }
        return PagamentoResponse.from(pagamento);
    }

    /**
     * Listagem paginada para admin.
     */
    @Transactional(readOnly = true)
    public Page<PagamentoResponse> listarAdmin(int page, int size, String statusFiltro) {
        int capSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, capSize, Sort.by(Sort.Direction.DESC, "criadoEm"));

        if (statusFiltro != null && !statusFiltro.isBlank()) {
            StatusPagamento status = StatusPagamento.valueOf(statusFiltro.toUpperCase());
            return pagamentoRepository.findByStatus(status, pageable).map(PagamentoResponse::from);
        }
        return pagamentoRepository.findAll(pageable).map(PagamentoResponse::from);
    }

    /**
     * Reembolso individual por cancelamento do participante (US-035 / US-042 individual).
     * Reusa reembolsar() + Reembolso.criar; idempotencia/ack-noop ficam no listener.
     * Retorna true se reembolsou (estava CONFIRMADO), false se no-op.
     * Estrategia de concorrencia: findByInscricaoIdForUpdate usa PESSIMISTIC_WRITE —
     * serializa corrida vs. reembolso em massa no mesmo pagamento (CR-S4-01).
     */
    @Transactional
    public boolean reembolsarPorInscricao(Long inscricaoId, String motivo) {
        var pagOpt = pagamentoRepository.findByInscricaoIdForUpdate(inscricaoId);
        if (pagOpt.isEmpty()) {
            log.info("Sem pagamento para inscricaoId={} — ACK no-op (gratuito/defesa)", inscricaoId);
            return false;
        }
        Pagamento p = pagOpt.get();
        if (!p.reembolsar()) {
            log.info("Pagamento inscricaoId={} nao-CONFIRMADO (status={}) — ACK no-op (CR-S4-01)",
                    inscricaoId, p.getStatus());
            return false;
        }
        pagamentoRepository.save(p);
        reembolsoRepository.save(Reembolso.criar(p.getId(), p.getUsuarioId(), p.getValorBruto(), motivo));
        log.info("Reembolso individual aplicado: inscricaoId={}, motivo={}", inscricaoId, motivo);
        return true;
    }

    private BigDecimal obterTaxaVigente() {
        return configuracaoRepository.findFirstByOrderByVigenteDesdeDesc()
                .map(ConfiguracaoPlataforma::getTaxaPercentual)
                .orElse(new BigDecimal("0.1000"));
    }
}
