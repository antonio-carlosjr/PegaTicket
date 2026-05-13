package com.ticketeira.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 160, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 120)
    private String senhaHash;

    @Column(nullable = false)
    private boolean verificado;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    protected Usuario() {
    }

    public Usuario(String nome, String email, String senhaHash) {
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.verificado = false;
        this.criadoEm = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public String getSenhaHash() { return senhaHash; }
    public boolean isVerificado() { return verificado; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }

    public void marcarComoVerificado() {
        this.verificado = true;
    }
}
