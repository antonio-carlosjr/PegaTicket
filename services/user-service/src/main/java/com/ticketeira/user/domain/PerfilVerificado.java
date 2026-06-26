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

    @Column(name = "email_contato", length = 160)
    private String emailContato;

    @Column(length = 9)
    private String cep;

    @Column(length = 160)
    private String logradouro;

    @Column(length = 20)
    private String numero;

    @Column(length = 80)
    private String complemento;

    @Column(length = 80)
    private String bairro;

    @Column(length = 80)
    private String cidade;

    @Column(length = 2)
    private String uf;

    @Column(length = 80)
    private String instagram;

    @Column(length = 200)
    private String website;

    @Column(name = "motivo_rejeicao", length = 300)
    private String motivoRejeicao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusVerificacao status;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    protected PerfilVerificado() {
    }

    public PerfilVerificado(Long usuarioId, String telefone, String cpf, String emailContato,
                            String cep, String logradouro, String numero, String complemento,
                            String bairro, String cidade, String uf, String instagram, String website) {
        this.usuarioId = usuarioId;
        this.telefone = telefone;
        this.cpf = cpf;
        this.emailContato = emailContato;
        this.cep = cep;
        this.logradouro = logradouro;
        this.numero = numero;
        this.complemento = complemento;
        this.bairro = bairro;
        this.cidade = cidade;
        this.uf = uf;
        this.instagram = instagram;
        this.website = website;
        this.status = StatusVerificacao.PENDENTE;
        this.criadoEm = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getTelefone() { return telefone; }
    public String getCpf() { return cpf; }
    public String getEmailContato() { return emailContato; }
    public String getCep() { return cep; }
    public String getLogradouro() { return logradouro; }
    public String getNumero() { return numero; }
    public String getComplemento() { return complemento; }
    public String getBairro() { return bairro; }
    public String getCidade() { return cidade; }
    public String getUf() { return uf; }
    public String getInstagram() { return instagram; }
    public String getWebsite() { return website; }
    public String getMotivoRejeicao() { return motivoRejeicao; }
    public StatusVerificacao getStatus() { return status; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }

    public void aprovar() { this.status = StatusVerificacao.VERIFICADO; }
    public void rejeitar(String motivo) { 
        this.status = StatusVerificacao.REJEITADO;
        this.motivoRejeicao = motivo;
    }
    public void reenviar() {
        this.status = StatusVerificacao.PENDENTE;
        this.motivoRejeicao = null;
    }
    
    public void atualizar(String telefone, String cpf, String emailContato,
                          String cep, String logradouro, String numero, String complemento,
                          String bairro, String cidade, String uf, String instagram, String website) {
        this.telefone = telefone;
        this.cpf = cpf;
        this.emailContato = emailContato;
        this.cep = cep;
        this.logradouro = logradouro;
        this.numero = numero;
        this.complemento = complemento;
        this.bairro = bairro;
        this.cidade = cidade;
        this.uf = uf;
        this.instagram = instagram;
        this.website = website;
    }
}
