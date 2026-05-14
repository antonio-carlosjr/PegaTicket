package com.ticketeira.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private OffsetDateTime expiraEm;

    @Column(name = "usado_em")
    private OffsetDateTime usadoEm;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    protected PasswordResetToken() {
    }

    public PasswordResetToken(Long usuarioId, String tokenHash, OffsetDateTime expiraEm) {
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.expiraEm = expiraEm;
        this.criadoEm = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getTokenHash() { return tokenHash; }
    public OffsetDateTime getExpiraEm() { return expiraEm; }
    public OffsetDateTime getUsadoEm() { return usadoEm; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }

    public boolean estaExpirado() {
        return OffsetDateTime.now().isAfter(expiraEm);
    }

    public boolean foiUsado() {
        return usadoEm != null;
    }

    public boolean estaValido() {
        return !foiUsado() && !estaExpirado();
    }

    public void marcarComoUsado() {
        if (foiUsado()) {
            throw new IllegalStateException("Token ja foi utilizado.");
        }
        this.usadoEm = OffsetDateTime.now();
    }
}
