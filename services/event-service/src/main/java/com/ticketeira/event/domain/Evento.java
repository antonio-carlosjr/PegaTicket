package com.ticketeira.event.domain;

import com.ticketeira.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "eventos")
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "data_inicio", nullable = false)
    private OffsetDateTime dataInicio;

    @Column(name = "data_fim", nullable = false)
    private OffsetDateTime dataFim;

    @Column(nullable = false, length = 200)
    private String local;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoEvento tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusEvento status;

    @Column(nullable = false)
    private Integer capacidade;

    @Column(precision = 12, scale = 2)
    private BigDecimal preco;

    @Column(name = "prazo_reembolso_dias")
    private Integer prazoReembolsoDias;

    @Column(name = "promotor_id", nullable = false)
    private Long promotorId;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    @Column(name = "vagas_disponiveis")
    private Integer vagasDisponiveis;

    @Column(name = "imagem_url", length = 300)
    private String imagemUrl;

    @Column(name = "realizado_em")
    private OffsetDateTime realizadoEm;

    @Column(name = "cancelado_em")
    private OffsetDateTime canceladoEm;

    protected Evento() {
    }

    public static Evento criar(Long promotorId, String titulo, String descricao,
                               OffsetDateTime dataInicio, OffsetDateTime dataFim,
                               String local, TipoEvento tipo, Integer capacidade,
                               BigDecimal preco, Integer prazoReembolsoDias, String imagemUrl) {
        Evento e = new Evento();
        e.promotorId = promotorId;
        e.titulo = titulo;
        e.descricao = descricao;
        e.dataInicio = dataInicio;
        e.dataFim = dataFim;
        e.local = local;
        e.tipo = tipo;
        e.capacidade = capacidade;
        e.preco = preco;
        e.prazoReembolsoDias = prazoReembolsoDias;
        e.imagemUrl = imagemUrl;
        e.status = StatusEvento.RASCUNHO;
        e.vagasDisponiveis = null;
        OffsetDateTime agora = OffsetDateTime.now();
        e.criadoEm = agora;
        e.atualizadoEm = agora;
        return e;
    }

    /** Maquina de estados: RASCUNHO → PUBLICADO. Inicializa vagasDisponiveis = capacidade. */
    public void publicar() {
        if (status == StatusEvento.PUBLICADO) {
            throw new BusinessException("EVENTO_JA_PUBLICADO", 409);
        }
        if (status != StatusEvento.RASCUNHO) {
            throw new BusinessException("TRANSICAO_INVALIDA", 409);
        }
        this.status = StatusEvento.PUBLICADO;
        this.vagasDisponiveis = this.capacidade;
        this.atualizadoEm = OffsetDateTime.now();
    }

    /** Maquina de estados: PUBLICADO → REALIZADO (gatilho do repasse). */
    public void realizar() {
        if (status == StatusEvento.REALIZADO) {
            throw new BusinessException("EVENTO_JA_REALIZADO", 409);
        }
        if (status != StatusEvento.PUBLICADO) {
            throw new BusinessException("TRANSICAO_INVALIDA", 409);
        }
        this.status = StatusEvento.REALIZADO;
        this.realizadoEm = OffsetDateTime.now();
        this.atualizadoEm = OffsetDateTime.now();
    }

    /** Maquina de estados: RASCUNHO|PUBLICADO → CANCELADO. */
    public void cancelar() {
        if (status == StatusEvento.CANCELADO) {
            throw new BusinessException("EVENTO_JA_CANCELADO", 409);
        }
        if (status == StatusEvento.REALIZADO) {
            throw new BusinessException("TRANSICAO_INVALIDA", 409);
        }
        this.status = StatusEvento.CANCELADO;
        this.canceladoEm = OffsetDateTime.now();
        this.vagasDisponiveis = this.capacidade;
        this.atualizadoEm = OffsetDateTime.now();
    }

    /** Atualiza dados — permitido apenas em RASCUNHO. */
    public void atualizarDados(String titulo, String descricao, OffsetDateTime dataInicio,
                               OffsetDateTime dataFim, String local, TipoEvento tipo,
                               Integer capacidade, BigDecimal preco, Integer prazoReembolsoDias,
                               String imagemUrl) {
        if (status != StatusEvento.RASCUNHO) {
            throw new BusinessException("EVENTO_NAO_EDITAVEL", 409);
        }
        this.titulo = titulo;
        this.descricao = descricao;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.local = local;
        this.tipo = tipo;
        this.capacidade = capacidade;
        this.preco = preco;
        this.prazoReembolsoDias = prazoReembolsoDias;
        this.imagemUrl = imagemUrl;
        this.atualizadoEm = OffsetDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        this.atualizadoEm = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getDescricao() { return descricao; }
    public OffsetDateTime getDataInicio() { return dataInicio; }
    public OffsetDateTime getDataFim() { return dataFim; }
    public String getLocal() { return local; }
    public TipoEvento getTipo() { return tipo; }
    public StatusEvento getStatus() { return status; }
    public Integer getCapacidade() { return capacidade; }
    public BigDecimal getPreco() { return preco; }
    public Integer getPrazoReembolsoDias() { return prazoReembolsoDias; }
    public Long getPromotorId() { return promotorId; }
    public OffsetDateTime getCriadoEm() { return criadoEm; }
    public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }
    public Integer getVagasDisponiveis() { return vagasDisponiveis; }
    public String getImagemUrl() { return imagemUrl; }
    public OffsetDateTime getRealizadoEm() { return realizadoEm; }
    public OffsetDateTime getCanceladoEm() { return canceladoEm; }
}
