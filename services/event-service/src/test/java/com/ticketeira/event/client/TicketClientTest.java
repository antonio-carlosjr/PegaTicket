package com.ticketeira.event.client;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.event.messaging.EventoPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * D4 — TicketClient (event-service outbound): MockRestServiceServer. [US-024]
 * Casos: D4.a D4.b
 *
 * Usa MockRestServiceServer (padrao Spring) em vez de WireMock para evitar nova dependencia.
 *
 * VERMELHO: TicketClient, TicketClientConfig nao existem no event-service.
 */
@Tag("unit")
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("D4 — TicketClient (MockRestServiceServer) — US-024")
class TicketClientTest {

    /** RestClient builder usado pelo TicketClient — injetado para criar o mock server. */
    @Autowired
    TicketClientConfig ticketClientConfig;

    @Autowired
    TicketClient ticketClient;

    @MockBean
    EventoPublisher eventoPublisher;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        mockServer = ticketClientConfig.mockServer();   // cria MockRestServiceServer do builder
    }

    // D4.a -------------------------------------------------------------------

    /**
     * D4.a — participou envia X-Internal-Token e mapeia {participou:true} -> true. [US-024 / ADR-T08]
     */
    @Test
    @DisplayName("D4.a — participou: envia X-Internal-Token + mapeia {participou:true} -> true")
    void participou_enviaToken_mapeiaTrue() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("/internal/tickets/participou")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", org.hamcrest.Matchers.notNullValue()))
                .andRespond(withSuccess("{\"participou\":true}", MediaType.APPLICATION_JSON));

        boolean resultado = ticketClient.participou(10L, 42L);

        assertThat(resultado).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("D4.a — participou: mapeia {participou:false} -> false")
    void participou_mapeiaFalse() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("/internal/tickets/participou")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"participou\":false}", MediaType.APPLICATION_JSON));

        boolean resultado = ticketClient.participou(10L, 42L);

        assertThat(resultado).isFalse();
        mockServer.verify();
    }

    // D4.b -------------------------------------------------------------------

    /**
     * D4.b — ticket responde 403 -> lanca TICKET_INDISPONIVEL/falha fechada (nao 500). [US-024 / resiliencia]
     */
    @Test
    @DisplayName("D4.b — ticket 403 -> TICKET_INDISPONIVEL 503 (falha fechada)")
    void participou_ticket403_lancaTicketIndisponivel() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("/internal/tickets/participou")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .body("{\"message\":\"ACESSO_INTERNO_NEGADO\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> ticketClient.participou(10L, 42L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("TICKET_INDISPONIVEL")
                .extracting("status").isEqualTo(503);
    }
}
