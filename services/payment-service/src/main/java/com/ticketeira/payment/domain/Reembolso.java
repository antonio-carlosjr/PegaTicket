package com.ticketeira.payment.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

/**
 * Entidade de reembolso. Sprint 5A: passa a ser escrita (EventoCanceladoListener).
 * motivo: 'EVENTO_CANCELADO' (5A) | 'CANCELAMENTO_PARTICIPANTE' (5B).
 */
@Entity
@Table(name = "reembolsos")
public class Reembolso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pagamento_id", nullable = false)
    private Long pagamentoId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "valor", nullable = false)
    private BigDecimal valor;

    @Column(name = "motivo", nullable = false, length = 40)
    private String motivo;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "solicitado_em", nullable = false)
    private OffsetDateTime solicitadoEm;

    @Column(name = "processado_em")
    private OffsetDateTime processadoEm;

    protected Reembolso() {}

    /**
     * Factory para criacao de reembolso simulado (status imediato = PROCESSADO).
     * valor recebe setScale(2, HALF_UP) — escala monetaria obrigatoria.
     */
    public static Reembolso criar(Long pagamentoId, Long usuarioId, BigDecimal valor, String motivo) {
        Reembolso r = new Reembolso();
        r.pagamentoId = pagamentoId;
        r.usuarioId = usuarioId;
        r.valor = valor.setScale(2, RoundingMode.HALF_UP);
        r.motivo = motivo;
        r.status = "PROCESSADO";
        r.solicitadoEm = OffsetDateTime.now();
        r.processadoEm = OffsetDateTime.now();
        return r;
    }

    public Long getId() { return id; }
    public Long getPagamentoId() { return pagamentoId; }
    public Long getUsuarioId() { return usuarioId; }
    public BigDecimal getValor() { return valor; }
    public String getMotivo() { return motivo; }
    public String getStatus() { return status; }
    public OffsetDateTime getSolicitadoEm() { return solicitadoEm; }
    public OffsetDateTime getProcessadoEm() { return processadoEm; }
}
