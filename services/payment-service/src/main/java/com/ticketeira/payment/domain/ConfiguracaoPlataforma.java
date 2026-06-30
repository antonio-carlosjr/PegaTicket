package com.ticketeira.payment.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Configuracao vigente da plataforma (taxa de escrow).
 * Leitura apenas no S4 — sem escrita/criacao por esta aplicacao.
 */
@Entity
@Table(name = "configuracao_plataforma")
public class ConfiguracaoPlataforma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "taxa_percentual", nullable = false)
    private BigDecimal taxaPercentual;

    @Column(name = "vigente_desde", nullable = false)
    private OffsetDateTime vigenteDesde;

    protected ConfiguracaoPlataforma() {}

    public Long getId() { return id; }
    public BigDecimal getTaxaPercentual() { return taxaPercentual; }
    public OffsetDateTime getVigenteDesde() { return vigenteDesde; }
}
