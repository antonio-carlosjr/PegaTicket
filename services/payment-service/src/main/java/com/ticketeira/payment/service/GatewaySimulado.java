package com.ticketeira.payment.service;

import com.ticketeira.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Gateway de pagamento simulado.
 * Modo normal: sempre aprova, retorna "SIM-{uuid}".
 * Modo recusa: propriedade app.gateway.simulado.recusar=true lanca PAGAMENTO_RECUSADO (402).
 */
@Component
public class GatewaySimulado {

    @Value("${app.gateway.simulado.recusar:false}")
    private boolean recusar;

    /**
     * Aprova o pagamento e retorna o id gerado pelo gateway.
     * Lanca BusinessException(402) se configurado para recusar.
     */
    public String aprovar(BigDecimal valor) {
        if (recusar) {
            throw new BusinessException("PAGAMENTO_RECUSADO", 402);
        }
        return "SIM-" + UUID.randomUUID();
    }

    /**
     * Versao de recusa explicita — utilizada pelo PagamentoService.confirmarComGatewayRecusando().
     */
    public String aprovarRecusando(BigDecimal valor) {
        throw new BusinessException("PAGAMENTO_RECUSADO", 402);
    }
}
