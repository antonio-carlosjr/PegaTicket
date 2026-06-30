package com.ticketeira.payment.controller;

import com.ticketeira.payment.TestcontainersBase;
import com.ticketeira.payment.messaging.PedidoCriadoEvent;
import com.ticketeira.payment.repository.PagamentoRepository;
import com.ticketeira.payment.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integracao do PaymentController via MockMvc (B5 + auth + consultas).
 * Requer Postgres + RabbitMQ reais (Testcontainers).
 * Cobre:
 * - B5.a: getInscricao_dono_retorna200
 * - B5.b: getInscricao_naoDono_retorna403
 * - B5.c: getPayments_admin_listaComValores
 * - B5.d: getPayments_naoAdmin_403
 * - B5.e: getMe_semHeader_401
 * Casos de teste: B5 (tests-spec.md)
 */
@Tag("integracao")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test-postgres")
@DisplayName("B5 — PaymentController (auth + consultas)")
class PaymentControllerIntegrationTest extends TestcontainersBase {

    private static final String EXCHANGE = "ticketeira.events";
    private static final String ROUTING_PEDIDO = "pedido.criado";

    @Autowired
    MockMvc mvc;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    PagamentoRepository pagamentoRepository;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @BeforeEach
    void limpar() {
        pagamentoRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    private void criarPagamentoPendente(Long inscricaoId, Long usuarioId, BigDecimal valor) {
        UUID eventId = UUID.randomUUID();
        PedidoCriadoEvent evento = new PedidoCriadoEvent(
                eventId, inscricaoId, usuarioId, 100L, valor, 5L, OffsetDateTime.now());
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_PEDIDO, evento);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(pagamentoRepository.findByInscricaoId(inscricaoId)).isPresent());
    }

    @Test
    @DisplayName("B5.e — getMe_semHeader_401")
    void getMe_semHeader_401() throws Exception {
        mvc.perform(get("/payments/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("B5.a — getInscricao_dono_retorna200ComValoresEscrow")
    void getInscricao_dono_retorna200ComValoresEscrow() throws Exception {
        Long inscricaoId = 10L;
        Long usuarioId = 20L;
        criarPagamentoPendente(inscricaoId, usuarioId, new BigDecimal("100.00"));

        mvc.perform(get("/payments/inscricao/{inscricaoId}", inscricaoId)
                        .header("X-User-Id", usuarioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inscricaoId").value(inscricaoId))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.valorBruto").value("100.00"))
                .andExpect(jsonPath("$.valorTaxa").value("10.00"))
                .andExpect(jsonPath("$.valorRepasse").value("90.00"));
    }

    @Test
    @DisplayName("B5.b — getInscricao_naoDono_retorna403")
    void getInscricao_naoDono_retorna403() throws Exception {
        Long inscricaoId = 11L;
        Long usuarioDono = 21L;
        Long usuarioOutro = 99L;
        criarPagamentoPendente(inscricaoId, usuarioDono, new BigDecimal("100.00"));

        mvc.perform(get("/payments/inscricao/{inscricaoId}", inscricaoId)
                        .header("X-User-Id", usuarioOutro))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("PAGAMENTO_DE_OUTRO_USUARIO"));
    }

    @Test
    @DisplayName("B5.c — getPayments_admin_listaComValoresEscrow")
    void getPayments_admin_listaComValores() throws Exception {
        criarPagamentoPendente(12L, 22L, new BigDecimal("100.00"));

        mvc.perform(get("/payments")
                        .header("X-User-Id", 1L)
                        .header("X-User-Papel", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].valorBruto").isNotEmpty())
                .andExpect(jsonPath("$.content[0].valorTaxa").isNotEmpty())
                .andExpect(jsonPath("$.content[0].valorRepasse").isNotEmpty());
    }

    @Test
    @DisplayName("B5.d — getPayments_naoAdmin_retorna403")
    void getPayments_naoAdmin_retorna403() throws Exception {
        mvc.perform(get("/payments")
                        .header("X-User-Id", 1L)
                        .header("X-User-Papel", "PARTICIPANTE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("ACESSO_NEGADO"));
    }

    @Test
    @DisplayName("Confirmar — semHeader_retorna401")
    void confirmar_semUserId_retorna401() throws Exception {
        mvc.perform(post("/payments/{inscricaoId}/confirmar", 1L))
                .andExpect(status().isUnauthorized());
    }
}
