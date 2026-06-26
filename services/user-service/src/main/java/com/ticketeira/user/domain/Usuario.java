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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Papel papel;

    @Column(nullable = false)
    private boolean verificado;

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    protected Usuario() {
    }

    private Usuario(String nome, String email, String senhaHash, Papel papel, boolean verificado) {
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.papel = papel;
        this.verificado = verificado;
        this.ativo = true;
        this.criadoEm = OffsetDateTime.now();
    }

    /** Cria um participante padrao (verificado, sem precisar aprovacao). */
    public static Usuario novoParticipante(String nome, String email, String senhaHash) {
        return new Usuario(nome, email, senhaHash, Papel.PARTICIPANTE, true);
    }

    /** Cria um promotor pendente (admin precisa aprovar). */
    public static Usuario novoPromotorPendente(String nome, String email, String senhaHash) {
        return new Usuario(nome, email, senhaHash, Papel.PARTICIPANTE, false);
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public String getSenhaHash() { return senhaHash; }
    public Papel getPapel() { return papel; }
    public boolean isVerificado() { return verificado; }
    public boolean isAtivo() { return ativo; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }

    public void marcarComoVerificado() {
        this.verificado = true;
    }

    public void atualizarNome(String nome) {
        this.nome = nome;
    }

    public void atualizarSenha(String novaSenhaHash) {
        this.senhaHash = novaSenhaHash;
    }

    public void ativar() { this.ativo = true; }
    public void inativar() { this.ativo = false; }
    
    public void promover() { this.papel = Papel.PROMOTOR; }
}
