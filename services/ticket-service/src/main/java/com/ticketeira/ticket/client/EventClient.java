package com.ticketeira.ticket.client;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.common.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Cliente REST do ticket-service para o event-service.
 * Usa RestClient (Spring 6.1, sincrono — sem dep nova).
 * Traduz erros HTTP em excecoes tipadas: nunca vira 500 silencioso.
 */
@Component
public class EventClient {

    private static final Logger log = LoggerFactory.getLogger(EventClient.class);

    private final RestClient restClient;

    public EventClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Valida se o evento existe, esta PUBLICADO e e GRATUITO.
     * Lanca excecoes tipadas para cada cenario de erro.
     */
    public EventResumo getEvento(Long eventoId) {
        try {
            return restClient.get()
                    .uri("/internal/events/{id}", eventoId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        if (resp.getStatusCode().value() == 404) {
                            throw new NotFoundException("EVENTO_NAO_ENCONTRADO");
                        }
                        throw new BusinessException("EVENTO_INDISPONIVEL", 503);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        throw new BusinessException("EVENTO_INDISPONIVEL", 503);
                    })
                    .body(EventResumo.class);
        } catch (ResourceAccessException e) {
            log.warn("event-service indisponivel ao buscar evento {}: {}", eventoId, e.getMessage());
            throw new BusinessException("EVENTO_INDISPONIVEL", 503);
        }
    }

    /**
     * Chama POST /internal/events/{id}/reservar-vaga.
     * Decremento atomico — NAO e idempotente, nao re-tentar.
     */
    public void reservarVaga(Long eventoId) {
        try {
            restClient.post()
                    .uri("/internal/events/{id}/reservar-vaga", eventoId)
                    .retrieve()
                    .onStatus(status -> status.value() == 409, (req, resp) -> {
                        throw new BusinessException("EVENTO_ESGOTADO", 409);
                    })
                    .onStatus(status -> status.value() == 404, (req, resp) -> {
                        throw new NotFoundException("EVENTO_NAO_ENCONTRADO");
                    })
                    .onStatus(status -> status.value() == 422, (req, resp) -> {
                        throw new BusinessException("EVENTO_NAO_PUBLICADO", 422);
                    })
                    .onStatus(status -> status.value() == 403, (req, resp) -> {
                        throw new BusinessException("EVENTO_INDISPONIVEL", 503);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        throw new BusinessException("EVENTO_INDISPONIVEL", 503);
                    })
                    .toBodilessEntity();
        } catch (ResourceAccessException e) {
            log.warn("event-service indisponivel ao reservar vaga do evento {}: {}", eventoId, e.getMessage());
            throw new BusinessException("EVENTO_INDISPONIVEL", 503);
        }
    }

    /**
     * Chama POST /internal/events/{id}/liberar-vaga (compensacao).
     * Idempotente no teto — falha nao deve propagar alem do log.
     * Lancada apenas se a chamada HTTP em si falhar (conexao/5xx).
     */
    public void liberarVaga(Long eventoId) {
        try {
            restClient.post()
                    .uri("/internal/events/{id}/liberar-vaga", eventoId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw new BusinessException("COMPENSACAO_FALHOU", 503);
                    })
                    .toBodilessEntity();
        } catch (ResourceAccessException e) {
            throw new BusinessException("COMPENSACAO_FALHOU", 503);
        }
    }
}
