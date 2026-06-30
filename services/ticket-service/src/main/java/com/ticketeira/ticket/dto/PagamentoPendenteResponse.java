package com.ticketeira.ticket.dto;

import java.math.BigDecimal;

/**
 * Referencia de checkout retornada pelo ticket-service ao criar inscricao PAGO.
 * NAO e o registro real de Pagamento (esse nasce no payment-service ao consumir pedido.criado).
 * Expos o proximo passo para o frontend redirecionar ao checkout.
 */
public record PagamentoPendenteResponse(
        Long inscricaoId,
        BigDecimal valor,   // = preco do evento
        String status       // sempre "AGUARDANDO" (pagamento ainda nao criado)
) {}
