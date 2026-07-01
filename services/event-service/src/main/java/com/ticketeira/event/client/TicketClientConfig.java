package com.ticketeira.event.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Configura o RestClient.Builder para chamadas internas event-service -> ticket-service.
 * Espelha EventClientConfig do ticket-service (ADR-T08 / ADR-T16).
 * Timeouts: connect 2s / read 3s. Header X-Internal-Token em todas as requisicoes.
 *
 * Expoe o builder (nao o RestClient construido) para que o TicketClientTest possa
 * instalar um MockRestServiceServer.bindTo(builder) antes de cada teste.
 * O TicketClient chama builder.build() em cada requisicao para pegar o factory corrente
 * (mockado em testes, real em producao).
 */
@Configuration
public class TicketClientConfig {

    final RestClient.Builder builder;

    public TicketClientConfig(
            @Value("${app.ticket-service.url:http://localhost:8083}") String ticketServiceUrl,
            @Value("${app.internal.token}") String internalToken) {

        ClientHttpRequestFactory factory = ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(Duration.ofSeconds(2))
                        .withReadTimeout(Duration.ofSeconds(3)));

        this.builder = RestClient.builder()
                .baseUrl(ticketServiceUrl)
                .requestFactory(factory)
                .defaultHeader("X-Internal-Token", internalToken);
    }

    @Bean
    public TicketClient ticketClient() {
        return new TicketClient(builder);
    }

    /**
     * Cria um MockRestServiceServer vinculado ao builder desta config.
     * Chamado no @BeforeEach do TicketClientTest (D4) para interceptar requisicoes
     * feitas pelo TicketClient durante o teste.
     */
    public MockRestServiceServer mockServer() {
        return MockRestServiceServer.bindTo(builder).build();
    }
}
