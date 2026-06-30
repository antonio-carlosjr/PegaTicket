package com.ticketeira.payment.exception;

import com.ticketeira.common.dto.ErrorResponse;
import com.ticketeira.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest req) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ErrorResponse body = ErrorResponse.of(400, "Bad Request", msg, req.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Parametro de path/query malformado (ex.: inscricaoId nao-numerico, status invalido em @RequestParam
     * convertido para enum). Cliente errou a entrada -> 400, nunca 500. (coding-standards / CR-S3-03)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String msg = "Parametro '" + ex.getName() + "' com valor invalido.";
        ErrorResponse body = ErrorResponse.of(400, "Bad Request", msg, req.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Valor de enum invalido em filtro (StatusPagamento.valueOf) e outros argumentos invalidos
     * vindos do cliente. Sem este handler, o catch-all transformaria em 500.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest req) {
        ErrorResponse body = ErrorResponse.of(400, "Bad Request",
                "Parametro de requisicao invalido.", req.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest req) {
        ErrorResponse body = ErrorResponse.of(400, "Bad Request",
                "Corpo da requisicao invalido.", req.getRequestURI());
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

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex,
                                                          HttpServletRequest req) {
        ErrorResponse body = ErrorResponse.of(404, "Not Found",
                "Recurso nao encontrado.", req.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Erro nao tratado", ex);
        ErrorResponse body = ErrorResponse.of(500, "Internal Server Error",
                "Erro inesperado.", req.getRequestURI());
        return ResponseEntity.internalServerError().body(body);
    }
}
