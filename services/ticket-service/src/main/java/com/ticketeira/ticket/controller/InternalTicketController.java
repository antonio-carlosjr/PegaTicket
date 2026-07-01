package com.ticketeira.ticket.controller;

import com.ticketeira.common.dto.ErrorResponse;
import com.ticketeira.ticket.domain.StatusInscricao;
import com.ticketeira.ticket.dto.ParticipacaoResponse;
import com.ticketeira.ticket.repository.IngressoRepository;
import com.ticketeira.ticket.repository.InscricaoRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Canal interno: event-service -> ticket-service (ADR-T08 / ADR-T16).
 * GET /internal/tickets/participou?usuarioId=&eventoId= — elegibilidade de avaliacao (US-024).
 * Nao roteado pelo gateway; autorizacao via X-Internal-Token (comparacao constante-no-tempo).
 * Espelha InternalEventController do event-service.
 */
@RestController
@RequestMapping("/internal/tickets")
public class InternalTicketController {

    private final InscricaoRepository inscricaoRepository;
    private final IngressoRepository ingressoRepository;
    private final String internalToken;

    public InternalTicketController(InscricaoRepository inscricaoRepository,
                                    IngressoRepository ingressoRepository,
                                    @Value("${app.internal.token}") String internalToken) {
        this.inscricaoRepository = inscricaoRepository;
        this.ingressoRepository = ingressoRepository;
        this.internalToken = internalToken;
    }

    /**
     * GET /internal/tickets/participou?usuarioId=&eventoId=
     * Retorna {participou: true} sse existe:
     *   - Ingresso UTILIZADO (de inscricao do usuario no evento), OU
     *   - Inscricao ATIVA para usuario+evento.
     * A condicao "evento REALIZADO" e pre-filtro do event-service (nao do ticket).
     */
    @GetMapping("/participou")
    public ResponseEntity<?> participou(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam Long usuarioId,
            @RequestParam Long eventoId,
            HttpServletRequest req) {

        if (!tokenValido(token)) {
            return acessoNegado(req);
        }

        // Verifica ingresso UTILIZADO (fez check-in) OU inscricao ATIVA
        boolean temIngressoUtilizado = ingressoRepository
                .existsIngressoUtilizadoByUsuarioIdAndEventoId(usuarioId, eventoId);
        boolean temInscricaoAtiva = inscricaoRepository
                .existsByUsuarioIdAndEventoIdAndStatus(usuarioId, eventoId, StatusInscricao.ATIVA);

        return ResponseEntity.ok(new ParticipacaoResponse(temIngressoUtilizado || temInscricaoAtiva));
    }

    /** Comparacao constante-no-tempo do segredo interno (evita timing attack; null-safe). */
    private boolean tokenValido(String token) {
        return token != null && MessageDigest.isEqual(
                internalToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<ErrorResponse> acessoNegado(HttpServletRequest req) {
        return ResponseEntity.status(403)
                .body(ErrorResponse.of(403, "Forbidden", "ACESSO_INTERNO_NEGADO", req.getRequestURI()));
    }
}
