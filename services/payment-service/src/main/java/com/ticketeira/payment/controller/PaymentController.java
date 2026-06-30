package com.ticketeira.payment.controller;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.payment.dto.PagamentoResponse;
import com.ticketeira.payment.service.PagamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints REST do payment-service.
 * Auth via headers injetados pelo gateway: X-User-Id, X-User-Papel.
 * Nao valida JWT diretamente (stateless, responsabilidade do gateway).
 */
@RestController
@RequestMapping("/payments")
@Tag(name = "Payments", description = "Gerenciamento de pagamentos e escrow")
public class PaymentController {

    private final PagamentoService pagamentoService;

    public PaymentController(PagamentoService pagamentoService) {
        this.pagamentoService = pagamentoService;
    }

    /**
     * POST /payments/{inscricaoId}/confirmar
     * Confirma o pagamento via gateway simulado. Idempotente.
     * Auth: X-User-Id obrigatorio.
     */
    @PostMapping("/{inscricaoId}/confirmar")
    @Operation(summary = "Confirmar pagamento", description = "Aprova o pagamento via gateway simulado e retem em escrow.")
    public ResponseEntity<PagamentoResponse> confirmar(
            @PathVariable Long inscricaoId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        if (userId == null) {
            throw new BusinessException("UNAUTHORIZED", 401);
        }

        PagamentoResponse response = pagamentoService.confirmar(inscricaoId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /payments/inscricao/{inscricaoId}
     * Status do pagamento de uma inscricao (ownership validado).
     * Auth: X-User-Id obrigatorio.
     */
    @GetMapping("/inscricao/{inscricaoId}")
    @Operation(summary = "Buscar pagamento por inscricao")
    public ResponseEntity<PagamentoResponse> buscarPorInscricao(
            @PathVariable Long inscricaoId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        if (userId == null) {
            throw new BusinessException("UNAUTHORIZED", 401);
        }

        PagamentoResponse response = pagamentoService.buscarPorInscricao(inscricaoId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /payments/me
     * Lista pagamentos do usuario autenticado (mais recente primeiro).
     * Auth: X-User-Id obrigatorio.
     */
    @GetMapping("/me")
    @Operation(summary = "Meus pagamentos")
    public ResponseEntity<List<PagamentoResponse>> meusPagementos(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        if (userId == null) {
            throw new BusinessException("UNAUTHORIZED", 401);
        }

        return ResponseEntity.ok(pagamentoService.listarPorUsuario(userId));
    }

    /**
     * GET /payments
     * Listagem paginada para admin. Exige papel ADMIN.
     * Auth: X-User-Id + X-User-Papel == ADMIN.
     */
    @GetMapping
    @Operation(summary = "Listar todos os pagamentos (admin)")
    public ResponseEntity<Page<PagamentoResponse>> listar(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Papel", required = false) String papel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {

        if (userId == null) {
            throw new BusinessException("UNAUTHORIZED", 401);
        }
        if (!"ADMIN".equals(papel)) {
            throw new BusinessException("ACESSO_NEGADO", 403);
        }

        return ResponseEntity.ok(pagamentoService.listarAdmin(page, size, status));
    }
}
