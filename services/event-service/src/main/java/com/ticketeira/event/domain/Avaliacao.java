package com.ticketeira.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;

@Entity
@Table(name = "avaliacoes",
       uniqueConstraints = @UniqueConstraint(name = "uk_avaliacao_usuario_evento",
               columnNames = {"evento_id", "usuario_id"}))
public class Avaliacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false)
    private Long eventoId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private Integer nota;

    @Column(columnDefinition = "TEXT")
    private String comentario;

    @Column(name = "avaliado_em", nullable = false)
    private OffsetDateTime avaliadoEm;

    protected Avaliacao() {}

    public static Avaliacao criar(Long eventoId, Long usuarioId, Integer nota, String comentario) {
        Avaliacao a = new Avaliacao();
        a.eventoId = eventoId;
        a.usuarioId = usuarioId;
        a.nota = nota;
        a.comentario = comentario;
        a.avaliadoEm = OffsetDateTime.now();
        return a;
    }

    public Long getId() { return id; }
    public Long getEventoId() { return eventoId; }
    public Long getUsuarioId() { return usuarioId; }
    public Integer getNota() { return nota; }
    public String getComentario() { return comentario; }
    public OffsetDateTime getAvaliadoEm() { return avaliadoEm; }
}
