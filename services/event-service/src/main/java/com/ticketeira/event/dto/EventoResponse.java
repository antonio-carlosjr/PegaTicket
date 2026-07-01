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
        OffsetDateTime atualizadoEm,
        ReputacaoResponse reputacao     // US-025: media+total de avaliacoes
) {
    /**
     * Factory com reputacao calculada pelo AvaliacaoRepository.agregarReputacao (US-025).
     */
    public static EventoResponse from(Evento e, ReputacaoResponse reputacao) {
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
                e.getAtualizadoEm(),
                reputacao
        );
    }

    /**
     * Factory sem reputacao: usada em endpoints de mutacao (criar/editar/publicar/cancelar/encerrar)
     * onde a reputacao nao e relevante para a resposta imediata.
     */
    public static EventoResponse from(Evento e) {
        return from(e, new ReputacaoResponse(null, 0L));
    }
}
