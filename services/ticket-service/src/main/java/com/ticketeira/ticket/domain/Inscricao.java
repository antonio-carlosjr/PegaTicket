package com.ticketeira.ticket.domain;

import com.ticketeira.common.exception.BusinessException;
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

    /** Factory para evento GRATUITO (S3) — status inicial ATIVA. */
    public static Inscricao criar(Long usuarioId, Long eventoId) {
        Inscricao i = new Inscricao();
        i.usuarioId = usuarioId;
        i.eventoId = eventoId;
        i.status = StatusInscricao.ATIVA;
        i.inscritoEm = OffsetDateTime.now();
        return i;
    }

    /** Factory para evento PAGO (S4) — vaga reservada, aguardando pagamento.aprovado. */
    public static Inscricao pendentePagamento(Long usuarioId, Long eventoId) {
        Inscricao i = new Inscricao();
        i.usuarioId = usuarioId;
        i.eventoId = eventoId;
        i.status = StatusInscricao.PENDENTE_PAGAMENTO;
        i.inscritoEm = OffsetDateTime.now();
        return i;
    }

    /**
     * Transicao PENDENTE_PAGAMENTO -> ATIVA.
     * Chamada pelo consumidor de pagamento.aprovado (saga).
     * Lanca em transicao invalida para proteger o invariante de dominio.
     */
    public void ativar() {
        if (status != StatusInscricao.PENDENTE_PAGAMENTO) {
            throw new BusinessException("TRANSICAO_INVALIDA_ATIVAR:" + status, 409);
        }
        this.status = StatusInscricao.ATIVA;
    }

    /**
     * Transicao PENDENTE_PAGAMENTO -> EXPIRADA.
     * Chamada pelo ExpiracaoReservaJob (TTL, ADR-T10).
     * Lanca em transicao invalida para evitar expirar inscricao ja ativa.
     */
    public void expirar() {
        if (status != StatusInscricao.PENDENTE_PAGAMENTO) {
            throw new BusinessException("TRANSICAO_INVALIDA_EXPIRAR:" + status, 409);
        }
        this.status = StatusInscricao.EXPIRADA;
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public Long getEventoId() { return eventoId; }
    public StatusInscricao getStatus() { return status; }
    public OffsetDateTime getInscritoEm() { return inscritoEm; }
}
