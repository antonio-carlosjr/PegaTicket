package com.ticketeira.ticket.controller;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.common.exception.UnauthorizedException;
import com.ticketeira.ticket.dto.CheckinRequest;
import com.ticketeira.ticket.dto.CheckinResponse;
import com.ticketeira.ticket.service.CheckinService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /tickets/checkin — check-in de ingresso por QR (US-034).
 * Exige X-User-Id + X-User-Papel==PROMOTOR (403 senao).
 * Ownership do evento validado no service (EventClient.getEvento, ADR-T14).
 */
@RestController
@RequestMapping("/tickets")
public class CheckinController {

    private final CheckinService checkinService;

    public CheckinController(CheckinService checkinService) {
        this.checkinService = checkinService;
    }

    @PostMapping("/checkin")
    public ResponseEntity<CheckinResponse> realizarCheckin(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Papel", required = false) String papel,
            @Valid @RequestBody CheckinRequest req) {

        if (userId == null) throw new UnauthorizedException("Autenticacao obrigatoria.");
        if (!"PROMOTOR".equals(papel)) throw new BusinessException("Acesso restrito a promotores.", 403);

        CheckinResponse resp = checkinService.realizarCheckin(req.codigoUnico(), userId);
        return ResponseEntity.ok(resp);
    }
}
