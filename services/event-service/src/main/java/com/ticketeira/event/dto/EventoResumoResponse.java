package com.ticketeira.event.dto;

import com.ticketeira.event.domain.Evento;
import com.ticketeira.event.domain.StatusEvento;
import com.ticketeira.event.domain.TipoEvento;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record EventoResumoResponse(
        Long id,
        String titulo,
        OffsetDateTime dataInicio,
        OffsetDateTime dataFim,
        String local,
        TipoEvento tipo,
        StatusEvento status,
        BigDecimal preco,
        Integer capacidade,
        String imagemUrl
) {
    public static EventoResumoResponse from(Evento e) {
        return new EventoResumoResponse(
                e.getId(),
                e.getTitulo(),
                e.getDataInicio(),
                e.getDataFim(),
                e.getLocal(),
                e.getTipo(),
                e.getStatus(),
                e.getPreco(),
                e.getCapacidade(),
                e.getImagemUrl()
        );
    }
}
