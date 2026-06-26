package com.ticketeira.event.controller;

import com.ticketeira.common.dto.ErrorResponse;
import com.ticketeira.event.dto.ReservaResponse;
import com.ticketeira.event.service.EventService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints internos: ticket-service → event-service.
 * Prefixo /internal/** NAO e roteado pelo gateway (ADR-T08 — defesa de roteamento).
 * Autorizacao via X-Internal-Token (segredo compartilhado, defesa de profundidade).
 */
@RestController
@RequestMapping("/internal/events")
public class InternalEventController {

    private final EventService eventService;
    private final String internalToken;

    public InternalEventController(EventService eventService,
                                   @Value("${app.internal.token}") String internalToken) {
        this.eventService = eventService;
        this.internalToken = internalToken;
    }

    /** POST /internal/events/{id}/reservar-vaga — decremento atomico de vagas. */
    @PostMapping("/{id}/reservar-vaga")
    public ResponseEntity<?> reservarVaga(
            @PathVariable Long id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            HttpServletRequest req) {
        if (!internalToken.equals(token)) {
            return ResponseEntity.status(403)
                    .body(ErrorResponse.of(403, "Forbidden", "ACESSO_INTERNO_NEGADO", req.getRequestURI()));
        }
        ReservaResponse resp = eventService.reservarVaga(id);
        return ResponseEntity.ok(resp);
    }

    /** POST /internal/events/{id}/liberar-vaga — compensacao (incremento limitado pela capacidade). */
    @PostMapping("/{id}/liberar-vaga")
    public ResponseEntity<?> liberarVaga(
            @PathVariable Long id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            HttpServletRequest req) {
        if (!internalToken.equals(token)) {
            return ResponseEntity.status(403)
                    .body(ErrorResponse.of(403, "Forbidden", "ACESSO_INTERNO_NEGADO", req.getRequestURI()));
        }
        ReservaResponse resp = eventService.liberarVaga(id);
        return ResponseEntity.ok(resp);
    }
}
