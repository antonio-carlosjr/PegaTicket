package com.ticketeira.ticket.controller;

import com.ticketeira.common.exception.UnauthorizedException;
import com.ticketeira.ticket.dto.CancelamentoResponse;
import com.ticketeira.ticket.service.CancelamentoInscricaoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DELETE /tickets/inscricoes/{id} — cancelamento voluntario de inscricao (US-035).
 * Exige X-User-Id; ownership validado no service (inscricao.usuarioId == userId).
 */
@RestController
@RequestMapping("/tickets")
public class CancelamentoController {

    private final CancelamentoInscricaoService cancelamentoService;

    public CancelamentoController(CancelamentoInscricaoService cancelamentoService) {
        this.cancelamentoService = cancelamentoService;
    }

    @DeleteMapping("/inscricoes/{id}")
    public ResponseEntity<CancelamentoResponse> cancelar(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id) {

        if (userId == null) throw new UnauthorizedException("Autenticacao obrigatoria.");
        CancelamentoResponse resp = cancelamentoService.cancelar(id, userId);
        return ResponseEntity.ok(resp);
    }
}
