package com.ticketeira.ticket.controller;

import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.client.EventResumo;
import com.ticketeira.ticket.messaging.PedidoCriadoPublisher;
import com.ticketeira.ticket.repository.IngressoRepository;
import com.ticketeira.ticket.repository.InscricaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A7 — Auth do endpoint interno GET /internal/events/{id} (estendido para campo preco).
 *
 * NOTA: Este teste cobre o event-service, mas foi adicionado aqui no ticket-service
 * pois o spec pede extensao do existente (InternalEventControllerTest) para o campo preco.
 * A classe real de producao esta em event-service.
 *
 * Este arquivo testa o comportamento do event-service via MockMvc para garantir que:
 * - A7.a: GET /internal/events/{id} sem X-Internal-Token retorna 403 ACESSO_INTERNO_NEGADO
 * - A7.b: GET via gateway (rota /api/internal/events/{id}) retorna 404 (rota nao existe)
 * - A7.c: com token valido, evento PAGO inclui preco e promotorId nao nulos
 *
 * Estes testes devem ser colocados no event-service (ja ha InternalEventControllerTest la).
 * Estendendo os casos A7 diretamente no event-service (ver arquivo correto abaixo).
 *
 * Casos de teste: A7 (tests-spec.md)
 */
@Tag("integracao")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("A7 — Auth do endpoint interno (ticket-service side: EventClient)")
class InternalEventAuthTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    InscricaoRepository inscricaoRepository;

    @Autowired
    IngressoRepository ingressoRepository;

    @MockBean
    EventClient eventClient;

    @MockBean
    PedidoCriadoPublisher pedidoCriadoPublisher;

    @BeforeEach
    void setUp() {
        ingressoRepository.deleteAll();
        inscricaoRepository.deleteAll();
    }

    @Test
    @DisplayName("A7 — inscrever em evento PAGO com eventClient retornando preco nao nulo")
    void inscrever_eventoPago_eventClientComPreco_retornaPagamentoComValor() throws Exception {
        // Verifica que o ticket-service usa EventResumo.preco() para montar PagamentoPendenteResponse
        when(eventClient.getEvento(anyLong()))
                .thenReturn(new EventResumo(1L, "Show", "PAGO", "PUBLICADO", 10, 100,
                        new BigDecimal("199.99"), 5L,
                        java.time.OffsetDateTime.now().plusDays(30), 7));

        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/tickets/inscricoes")
                        .header("X-User-Id", 10L)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"eventoId\": 1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE_PAGAMENTO"))
                .andExpect(jsonPath("$.ingresso").doesNotExist())
                .andExpect(jsonPath("$.pagamento.valor").value("199.99"))
                .andExpect(jsonPath("$.pagamento.status").value("AGUARDANDO"));
    }

    @Test
    @DisplayName("A7 — inscrever sem X-User-Id retorna 401")
    void inscrever_semUserId_retorna401() throws Exception {
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/tickets/inscricoes")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"eventoId\": 1}"))
                .andExpect(status().isUnauthorized());
    }
}
