package com.ticketeira.event.dto;

import com.ticketeira.event.domain.Evento;
import com.ticketeira.event.domain.StatusEvento;
import com.ticketeira.event.domain.TipoEvento;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record EventoResponse(
        Long id,
        String titulo,
        String descricao,
        OffsetDateTime dataInicio,
        OffsetDateTime dataFim,
        String local,
        TipoEvento tipo,
        StatusEvento status,
        Integer capacidade,
        Integer vagasDisponiveis,
        BigDecimal preco,
        Integer prazoReembolsoDias,
        String imagemUrl,
        Long promotorId,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm
) {
    public static EventoResponse from(Evento e) {
        return new EventoResponse(
                e.getId(),
                e.getTitulo(),
                e.getDescricao(),
                e.getDataInicio(),
                e.getDataFim(),
                e.getLocal(),
                e.getTipo(),
                e.getStatus(),
                e.getCapacidade(),
                e.getVagasDisponiveis(),
                e.getPreco(),
                e.getPrazoReembolsoDias(),
                e.getImagemUrl(),
                e.getPromotorId(),
                e.getCriadoEm(),
                e.getAtualizadoEm()
        );
    }
}
