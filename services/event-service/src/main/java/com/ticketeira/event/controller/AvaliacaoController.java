package com.ticketeira.event.controller;

import com.ticketeira.common.exception.UnauthorizedException;
import com.ticketeira.event.dto.AvaliacaoRequest;
import com.ticketeira.event.dto.AvaliacaoResponse;
import com.ticketeira.event.service.AvaliacaoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /events/{id}/avaliacoes — avaliacao por participante elegivel (US-024).
 * nota fora de 1-5 -> 400 (Bean Validation @Min/@Max na borda).
 */
@RestController
@RequestMapping("/events")
public class AvaliacaoController {

    private final AvaliacaoService avaliacaoService;

    public AvaliacaoController(AvaliacaoService avaliacaoService) {
        this.avaliacaoService = avaliacaoService;
    }

    @PostMapping("/{id}/avaliacoes")
    public ResponseEntity<AvaliacaoResponse> avaliar(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody AvaliacaoRequest req) {
        if (userId == null) throw new UnauthorizedException("Autenticacao obrigatoria.");
        var avaliacao = avaliacaoService.avaliar(id, userId, req.nota(), req.comentario());
        return ResponseEntity.status(201).body(AvaliacaoResponse.from(avaliacao));
    }
}
