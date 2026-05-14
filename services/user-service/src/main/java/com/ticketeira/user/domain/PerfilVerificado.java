package com.ticketeira.user.domain;

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
@Table(name = "perfis_verificados")
public class PerfilVerificado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, unique = true)
    private Long usuarioId;

    @Column(nullable = false, length = 20)
    private String telefone;

    @Column(nullable = false, length = 14, unique = true)
    private String cpf;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusVerificacao status;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    protected PerfilVerificado() {
    }

    public PerfilVerificado(Long usuarioId, String telefone, String cpf) {
        this.usuarioId = usuarioId;
        this.telefone = telefone;
        this.cpf = cpf;
        this.status = StatusVerificacao.PENDENTE;
        this.criadoEm = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getTelefone() { return telefone; }
    public String getCpf() { return cpf; }
    public StatusVerificacao getStatus() { return status; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }

    public void aprovar() { this.status = StatusVerificacao.VERIFICADO; }
    public void rejeitar() { this.status = StatusVerificacao.REJEITADO; }
}
