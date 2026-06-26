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

@Entity
@Table(name = "inscricoes")
public class Inscricao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "evento_id", nullable = false)
    private Long eventoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusInscricao status;

    @Column(name = "inscrito_em", nullable = false)
    private OffsetDateTime inscritoEm;

    protected Inscricao() {}

    public static Inscricao criar(Long usuarioId, Long eventoId) {
        Inscricao i = new Inscricao();
        i.usuarioId = usuarioId;
        i.eventoId = eventoId;
        i.status = StatusInscricao.ATIVA;
        i.inscritoEm = OffsetDateTime.now();
        return i;
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public Long getEventoId() { return eventoId; }
    public StatusInscricao getStatus() { return status; }
    public OffsetDateTime getInscritoEm() { return inscritoEm; }
}
