package com.ticketeira.event.client;

import com.ticketeira.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Cliente REST do event-service para o ticket-service.
 * Canal interno: /internal/tickets/** + X-Internal-Token (ADR-T08 / ADR-T16).
 * Falha fechada: erro/403 do interno -> excecao tipada (503), nunca 500 (PA-02).
 *
 * Recebe o RestClient.Builder (nao o RestClient pronto) para permitir que
 * MockRestServiceServer.bindTo(builder) seja instalado em testes (D4).
 */
public class TicketClient {

    private static final Logger log = LoggerFactory.getLogger(TicketClient.class);

    private final RestClient.Builder builder;

    public TicketClient(RestClient.Builder builder) {
        this.builder = builder;
    }

    /**
     * Verifica se o usuario participou do evento (US-024 / ADR-T16).
     * Retorna true sse existe Ingresso UTILIZADO ou Inscricao ATIVA para o par (usuarioId, eventoId).
     * Lanca BusinessException("TICKET_INDISPONIVEL", 503) em caso de falha de comunicacao ou 403 (mis-config).
     */
    public boolean participou(Long usuarioId, Long eventoId) {
        try {
            ParticipacaoResponse resp = builder.build().get()
                    .uri("/internal/tickets/participou?usuarioId={u}&eventoId={e}", usuarioId, eventoId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        int sc = res.getStatusCode().value();
                        if (sc == 403) {
                            log.error("X-Internal-Token rejeitado pelo ticket-service (403) ao verificar "
                                    + "participacao do usuario {} no evento {} — conferir INTERNAL_TOKEN "
                                    + "identico nos dois servicos", usuarioId, eventoId);
                        } else {
                            log.warn("ticket-service respondeu {} ao verificar participacao {}/{}", sc, usuarioId, eventoId);
                        }
                        throw new BusinessException("TICKET_INDISPONIVEL", 503);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new BusinessException("TICKET_INDISPONIVEL", 503);
                    })
                    .body(ParticipacaoResponse.class);
            return resp != null && resp.participou();
        } catch (ResourceAccessException e) {
            log.warn("ticket-service indisponivel ao verificar participacao {}/{}: {}", usuarioId, eventoId, e.getMessage());
            throw new BusinessException("TICKET_INDISPONIVEL", 503);
        }
    }

    /** DTO interno para deserializar a resposta de /internal/tickets/participou. */
    record ParticipacaoResponse(boolean participou) {}
}
