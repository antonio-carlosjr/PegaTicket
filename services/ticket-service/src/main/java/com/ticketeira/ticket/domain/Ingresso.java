package com.ticketeira.ticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ingressos")
public class Ingresso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inscricao_id", nullable = false, unique = true)
    private Long inscricaoId;

    @Column(name = "codigo_unico", nullable = false, unique = true, length = 64)
    private String codigoUnico;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusIngresso status;

    @Column(name = "emitido_em", nullable = false)
    private OffsetDateTime emitidoEm;

    protected Ingresso() {}

    public static Ingresso emitir(Long inscricaoId) {
        Ingresso ing = new Ingresso();
        ing.inscricaoId = inscricaoId;
        ing.codigoUnico = UUID.randomUUID().toString();
        ing.status = StatusIngresso.ATIVO;
        ing.emitidoEm = OffsetDateTime.now();
        return ing;
    }

    public Long getId() { return id; }
    public Long getInscricaoId() { return inscricaoId; }
    public String getCodigoUnico() { return codigoUnico; }
    public StatusIngresso getStatus() { return status; }
    public OffsetDateTime getEmitidoEm() { return emitidoEm; }
}
