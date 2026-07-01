package com.ticketeira.ticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Registro de check-in de um ingresso (US-034).
 * Tabela checkins ja existe (V1). UNIQUE(ingresso_id) e a barreira anti-duplo-check-in.
 */
@Entity
@Table(name = "checkins")
public class Checkin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ingresso_id", nullable = false, unique = true)
    private Long ingressoId;

    @Column(name = "realizado_em", nullable = false)
    private OffsetDateTime realizadoEm;

    @Column(nullable = false)
    private boolean valido;

    protected Checkin() {}

    /** Factory: registra o check-in de um ingresso. */
    public static Checkin de(Long ingressoId) {
        Checkin c = new Checkin();
        c.ingressoId = ingressoId;
        c.realizadoEm = OffsetDateTime.now();
        c.valido = true;
        return c;
    }

    public Long getId() { return id; }
    public Long getIngressoId() { return ingressoId; }
    public OffsetDateTime getRealizadoEm() { return realizadoEm; }
    public boolean isValido() { return valido; }
}
