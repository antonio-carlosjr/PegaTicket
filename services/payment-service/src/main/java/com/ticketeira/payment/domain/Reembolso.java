package com.ticketeira.payment.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Entidade mapeada apenas para satisfazer ddl-auto:validate (tabela reembolsos existe no V1).
 * Sem escrita neste sprint (S4) — operacoes de reembolso sao implementadas no S5.
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

    public Long getId() { return id; }
    public Long getPagamentoId() { return pagamentoId; }
    public Long getUsuarioId() { return usuarioId; }
    public BigDecimal getValor() { return valor; }
    public String getMotivo() { return motivo; }
    public String getStatus() { return status; }
    public OffsetDateTime getSolicitadoEm() { return solicitadoEm; }
    public OffsetDateTime getProcessadoEm() { return processadoEm; }
}
