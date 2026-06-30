package com.ticketeira.payment.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

/**
 * Entidade raiz do payment-service.
 * Encapsula o calculo de escrow e a transicao de estado.
 */
@Entity
@Table(name = "pagamentos")
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inscricao_id", nullable = false, unique = true)
    private Long inscricaoId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "evento_id")
    private Long eventoId;

    @Column(name = "promotor_id")
    private Long promotorId;

    @Column(name = "valor_bruto", nullable = false)
    private BigDecimal valorBruto;

    @Column(name = "valor_taxa", nullable = false)
    private BigDecimal valorTaxa;

    @Column(name = "valor_repasse", nullable = false)
    private BigDecimal valorRepasse;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusPagamento status;

    @Column(name = "gateway", nullable = false, length = 50)
    private String gateway;

    @Column(name = "gateway_payment_id", length = 80)
    private String gatewayPaymentId;

    @Column(name = "processado_em")
    private OffsetDateTime processadoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "repassado_em")
    private OffsetDateTime repassadoEm;

    @Column(name = "reembolsado_em")
    private OffsetDateTime reembolsadoEm;

    protected Pagamento() {}

    /**
     * Factory legada (4 params): compatibilidade com testes unitarios pre-S5A.
     * Delega ao factory principal com eventoId/promotorId nulos.
     */
    public static Pagamento pendente(Long inscricaoId, Long usuarioId,
                                     BigDecimal valorBruto, BigDecimal taxaPercentual) {
        return pendente(inscricaoId, usuarioId, null, null, valorBruto, taxaPercentual);
    }

    /**
     * Factory principal: cria pagamento PENDENTE com escrow computado e vinculo ao evento/promotor.
     * eventoId/promotorId vindos do PedidoCriadoEvent (TECH-S4-01).
     */
    public static Pagamento pendente(Long inscricaoId, Long usuarioId, Long eventoId, Long promotorId,
                                     BigDecimal valorBruto, BigDecimal taxaPercentual) {
        Pagamento p = new Pagamento();
        p.inscricaoId = inscricaoId;
        p.usuarioId = usuarioId;
        p.eventoId = eventoId;
        p.promotorId = promotorId;
        p.valorBruto = valorBruto.setScale(2, RoundingMode.HALF_UP);
        p.valorTaxa = p.valorBruto.multiply(taxaPercentual).setScale(2, RoundingMode.HALF_UP);
        p.valorRepasse = p.valorBruto.subtract(p.valorTaxa);
        p.status = StatusPagamento.PENDENTE;
        p.gateway = "SIMULADO";
        p.criadoEm = OffsetDateTime.now();
        return p;
    }

    /**
     * Transicao idempotente PENDENTE -> CONFIRMADO.
     * Retorna true se a transicao ocorreu; false se ja estava CONFIRMADO (no-op — nao re-publicar).
     */
    public boolean confirmar(String gatewayPaymentId) {
        if (this.status == StatusPagamento.CONFIRMADO) {
            return false;
        }
        this.status = StatusPagamento.CONFIRMADO;
        this.gatewayPaymentId = gatewayPaymentId;
        this.processadoEm = OffsetDateTime.now();
        return true;
    }

    /**
     * CONFIRMADO -> REPASSADO. Idempotente/condicional: retorna false se nao estava CONFIRMADO.
     */
    public boolean repassar() {
        if (this.status != StatusPagamento.CONFIRMADO) return false;
        this.status = StatusPagamento.REPASSADO;
        this.repassadoEm = OffsetDateTime.now();
        return true;
    }

    /**
     * CONFIRMADO -> REEMBOLSADO. Idempotente/condicional: retorna false se nao estava CONFIRMADO.
     */
    public boolean reembolsar() {
        if (this.status != StatusPagamento.CONFIRMADO) return false;
        this.status = StatusPagamento.REEMBOLSADO;
        this.reembolsadoEm = OffsetDateTime.now();
        return true;
    }

    // --- getters (sem setters publicos — mutacao via metodos de dominio) ---

    public Long getId() { return id; }
    public Long getInscricaoId() { return inscricaoId; }
    public Long getUsuarioId() { return usuarioId; }
    public Long getEventoId() { return eventoId; }
    public Long getPromotorId() { return promotorId; }
    public BigDecimal getValorBruto() { return valorBruto; }
    public BigDecimal getValorTaxa() { return valorTaxa; }
    public BigDecimal getValorRepasse() { return valorRepasse; }
    public StatusPagamento getStatus() { return status; }
    public String getGateway() { return gateway; }
    public String getGatewayPaymentId() { return gatewayPaymentId; }
    public OffsetDateTime getProcessadoEm() { return processadoEm; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public OffsetDateTime getRepassadoEm() { return repassadoEm; }
    public OffsetDateTime getReembolsadoEm() { return reembolsadoEm; }
}
