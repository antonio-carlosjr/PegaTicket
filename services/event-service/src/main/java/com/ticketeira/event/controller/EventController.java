package com.ticketeira.event.controller;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.common.exception.UnauthorizedException;
import com.ticketeira.event.domain.TipoEvento;
import com.ticketeira.event.dto.EventoCreateRequest;
import com.ticketeira.event.dto.EventoResponse;
import com.ticketeira.event.dto.EventoResumoResponse;
import com.ticketeira.event.dto.EventoUpdateRequest;
import com.ticketeira.event.service.EventService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    private void requirePromotor(String papel) {
        if (!"PROMOTOR".equals(papel)) {
            throw new BusinessException("Acesso restrito a promotores.", 403);
        }
    }

    private Long requireUserId(Long userId) {
        if (userId == null) throw new UnauthorizedException("Autenticacao obrigatoria.");
        return userId;
    }

    /** POST /events — cria evento (PROMOTOR) */
    @PostMapping
    public ResponseEntity<EventoResponse> criar(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Papel", required = false) String papel,
            @Valid @RequestBody EventoCreateRequest req) {
        requireUserId(userId);
        requirePromotor(papel);
        var evento = eventService.criar(userId, req);
        URI location = URI.create("/events/" + evento.getId());
        return ResponseEntity.created(location).body(EventoResponse.from(evento));
    }

    /**
     * GET /events/meus — meus eventos (PROMOTOR).
     * IMPORTANTE: declarado antes de GET /events/{id} para evitar ambiguidade de rota.
     */
    @GetMapping("/meus")
    public ResponseEntity<Page<EventoResumoResponse>> listarMeus(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Papel", required = false) String papel,
            Pageable pageable) {
        requireUserId(userId);
        requirePromotor(papel);
        return ResponseEntity.ok(eventService.listarMeus(userId, pageable).map(EventoResumoResponse::from));
    }

    /** PUT /events/{id} — edita (PROMOTOR + owner, apenas RASCUNHO) */
    @PutMapping("/{id}")
    public ResponseEntity<EventoResponse> editar(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Papel", required = false) String papel,
            @Valid @RequestBody EventoUpdateRequest req) {
        requireUserId(userId);
        requirePromotor(papel);
        return ResponseEntity.ok(EventoResponse.from(eventService.editar(userId, id, req)));
    }

    /** POST /events/{id}/publicar — RASCUNHO → PUBLICADO (PROMOTOR + owner) */
    @PostMapping("/{id}/publicar")
    public ResponseEntity<EventoResponse> publicar(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Papel", required = false) String papel) {
        requireUserId(userId);
        requirePromotor(papel);
        return ResponseEntity.ok(EventoResponse.from(eventService.publicar(userId, id)));
    }

    /** POST /events/{id}/cancelar — RASCUNHO|PUBLICADO → CANCELADO (PROMOTOR + owner) */
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<EventoResponse> cancelar(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Papel", required = false) String papel) {
        requireUserId(userId);
        requirePromotor(papel);
        return ResponseEntity.ok(EventoResponse.from(eventService.cancelar(userId, id)));
    }

    /** GET /events — lista eventos PUBLICADOS com filtros opcionais (qualquer autenticado) */
    @GetMapping
    public ResponseEntity<Page<EventoResumoResponse>> listar(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TipoEvento tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime ate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "dataInicio,asc") String sort) {
        requireUserId(userId);
        // Defesa contra size gigante
        int cappedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, cappedSize);
        return ResponseEntity.ok(
                eventService.listarPublicados(q, tipo, de, ate, pageable).map(EventoResumoResponse::from));
    }

    /** GET /events/{id} — detalhe (PUBLICADO: qualquer autenticado; RASCUNHO/outros: apenas owner) */
    @GetMapping("/{id}")
    public ResponseEntity<EventoResponse> detalhe(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUserId(userId);
        return ResponseEntity.ok(EventoResponse.from(eventService.detalhe(userId, id)));
    }
}
