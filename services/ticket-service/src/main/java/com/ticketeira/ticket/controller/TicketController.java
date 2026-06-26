package com.ticketeira.ticket.controller;

import com.ticketeira.common.exception.UnauthorizedException;
import com.ticketeira.ticket.dto.InscricaoHistoricoResponse;
import com.ticketeira.ticket.dto.InscricaoRequest;
import com.ticketeira.ticket.dto.InscricaoResponse;
import com.ticketeira.ticket.dto.MeuIngressoResponse;
import com.ticketeira.ticket.service.InscricaoService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/tickets")
public class TicketController {

    private final InscricaoService inscricaoService;

    public TicketController(InscricaoService inscricaoService) {
        this.inscricaoService = inscricaoService;
    }

    /**
     * POST /tickets/inscricoes — inscreve o usuario autenticado em evento GRATUITO publicado.
     * Dispara a mini-saga (validar → reservar → tx local).
     */
    @PostMapping("/inscricoes")
    public ResponseEntity<InscricaoResponse> inscrever(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody InscricaoRequest req) {
        if (userId == null) throw new UnauthorizedException("Autenticacao obrigatoria.");
        InscricaoResponse resp = inscricaoService.inscrever(req.eventoId(), userId);
        URI location = URI.create("/tickets/inscricoes/" + resp.id());
        return ResponseEntity.created(location).body(resp);
    }

    /**
     * GET /tickets/me — lista ingressos do usuario autenticado (para tela QR).
     */
    @GetMapping("/me")
    public ResponseEntity<List<MeuIngressoResponse>> meusIngressos(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) throw new UnauthorizedException("Autenticacao obrigatoria.");
        return ResponseEntity.ok(inscricaoService.meusIngressos(userId));
    }

    /**
     * GET /tickets/inscricoes/me — historico paginado de inscricoes (mais recente primeiro).
     */
    @GetMapping("/inscricoes/me")
    public ResponseEntity<Page<InscricaoHistoricoResponse>> historicoInscricoes(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (userId == null) throw new UnauthorizedException("Autenticacao obrigatoria.");
        int cappedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, cappedSize);
        return ResponseEntity.ok(inscricaoService.historicoInscricoes(userId, pageable));
    }
}
