package com.ticketeira.payment.exception;

import com.ticketeira.payment.controller.PaymentController;
import com.ticketeira.payment.service.PagamentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regressao CR-S4-03 (P1, coding-standards / CR-S3-03): nenhum caminho de cliente vira 500.
 * - inscricaoId nao-numerico no path -> 400 (MethodArgumentTypeMismatchException)
 * - status de filtro invalido (StatusPagamento.valueOf falha) -> 400 (IllegalArgumentException),
 *   nao 500 pelo catch-all.
 *
 * Teste rapido (standalone MockMvc, sem contexto Spring/DB).
 */
@DisplayName("CR-S4-03 — GlobalExceptionHandler do payment (cliente nunca 500)")
class GlobalExceptionHandlerWebTest {

    private MockMvc mvc;
    private PagamentoService service;

    @BeforeEach
    void setup() {
        service = Mockito.mock(PagamentoService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PaymentController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("inscricaoId nao-numerico no path -> 400, nao 500")
    void confirmar_inscricaoIdInvalida_retorna400() throws Exception {
        mvc.perform(post("/payments/{inscricaoId}/confirmar", "abc")
                        .header("X-User-Id", 1L))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("status de filtro invalido (IllegalArgumentException) -> 400, nao 500")
    void listar_statusInvalido_retorna400() throws Exception {
        when(service.listarAdmin(anyInt(), anyInt(), anyString()))
                .thenThrow(new IllegalArgumentException("No enum constant StatusPagamento.FOO"));

        mvc.perform(get("/payments")
                        .header("X-User-Id", 1L)
                        .header("X-User-Papel", "ADMIN")
                        .param("status", "FOO"))
                .andExpect(status().isBadRequest());
    }
}
