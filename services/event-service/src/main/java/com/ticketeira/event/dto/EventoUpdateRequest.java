package com.ticketeira.event.dto;

import com.ticketeira.event.domain.TipoEvento;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record EventoUpdateRequest(
        @NotBlank @Size(max = 160) String titulo,
        @Size(max = 5000) String descricao,
        @NotNull OffsetDateTime dataInicio,
        @NotNull OffsetDateTime dataFim,
        @NotBlank @Size(max = 200) String local,
        @NotNull TipoEvento tipo,
        @NotNull @Positive Integer capacidade,
        @PositiveOrZero BigDecimal preco,
        @PositiveOrZero Integer prazoReembolsoDias,
        @Size(max = 300) String imagemUrl
) {
    @AssertTrue(message = "data_fim deve ser maior ou igual a data_inicio")
    public boolean isPeriodoValido() {
        return dataInicio == null || dataFim == null || !dataFim.isBefore(dataInicio);
    }

    @AssertTrue(message = "Evento PAGO exige preco > 0 e prazo de reembolso; GRATUITO nao deve ter preco")
    public boolean isPrecoCoerente() {
        if (tipo == null) return true;
        if (tipo == TipoEvento.PAGO) {
            return preco != null && preco.signum() > 0 && prazoReembolsoDias != null;
        }
        return preco == null;
    }
}
