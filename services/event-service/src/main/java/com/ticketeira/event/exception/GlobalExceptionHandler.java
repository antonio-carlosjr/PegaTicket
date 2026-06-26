package com.ticketeira.event.exception;

import com.ticketeira.common.dto.ErrorResponse;
import com.ticketeira.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest req) {
        ErrorResponse body = ErrorResponse.of(
                ex.getStatus(),
                HttpStatus.valueOf(ex.getStatus()).getReasonPhrase(),
                ex.getMessage(),
                req.getRequestURI()
        );
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        // Tambem captura erros de @AssertTrue (class-level)
        String classMsg = ex.getBindingResult().getGlobalErrors().stream()
                .map(e -> e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        String fullMsg = msg.isEmpty() ? classMsg : (classMsg.isEmpty() ? msg : msg + "; " + classMsg);
        ErrorResponse body = ErrorResponse.of(400, "Bad Request", fullMsg, req.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Parametro de query/path com tipo invalido (ex.: tipo=FOO, de=2026-06-20T14:00 sem offset,
     * id nao-numerico). Sem este handler, cai no generico → 500, contrariando o contrato
     * (api-contracts.md §6: filtro malformado deve ser 400, nao 500).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String msg = "Parametro '" + ex.getName() + "' com valor invalido.";
        ErrorResponse body = ErrorResponse.of(400, "Bad Request", msg, req.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            org.springframework.dao.DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Violacao de integridade: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of(409, "Conflict",
                "Operacao resultou em conflito de dados.", req.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Erro nao tratado", ex);
        ErrorResponse body = ErrorResponse.of(500, "Internal Server Error", "Erro inesperado.", req.getRequestURI());
        return ResponseEntity.internalServerError().body(body);
    }
}
