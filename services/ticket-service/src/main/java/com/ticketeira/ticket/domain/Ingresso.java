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

    /**
     * Marca o ingresso como UTILIZADO (check-in).
     * Idempotente: no-op se ja UTILIZADO. Lanca em estado CANCELADO
     * (nao faz sentido dar check-in num ingresso cancelado).
     */
    public void utilizar() {
        if (status == StatusIngresso.UTILIZADO) return;
        if (status == StatusIngresso.CANCELADO) {
            throw new IllegalStateException("Ingresso cancelado nao pode ser utilizado.");
        }
        this.status = StatusIngresso.UTILIZADO;
    }

    /**
     * Check-in da porta (US-034): ATIVO -> UTILIZADO.
     * DIFERENTE de utilizar() (5A): aqui o 2o check-in deve FALHAR com 409
     * (criterio US-034.2), nao ser no-op idempotente.
     * - ATIVO     -> UTILIZADO (sucesso)
     * - UTILIZADO -> lanca BusinessException("INGRESSO_JA_UTILIZADO", 409)
     * - CANCELADO -> nao reaproveita; lanca BusinessException("INGRESSO_JA_UTILIZADO", 409)
     */
    public void realizarCheckin() {
        if (status == StatusIngresso.UTILIZADO) {
            throw new BusinessException("INGRESSO_JA_UTILIZADO", 409);
        }
        if (status == StatusIngresso.CANCELADO) {
            throw new BusinessException("INGRESSO_JA_UTILIZADO", 409);
        }
        this.status = StatusIngresso.UTILIZADO;
    }

    /**
     * Cancela o ingresso em decorrencia de cancelamento do evento (US-042).
     * Idempotente: no-op (false) se ja CANCELADO ou UTILIZADO — preserva historico de check-in.
     */
    public boolean cancelar() {
        if (status == StatusIngresso.ATIVO) {
            this.status = StatusIngresso.CANCELADO;
            return true;
        }
        return false;
    }

    public Long getId() { return id; }
    public Long getInscricaoId() { return inscricaoId; }
    public String getCodigoUnico() { return codigoUnico; }
    public StatusIngresso getStatus() { return status; }
    public OffsetDateTime getEmitidoEm() { return emitidoEm; }
}
