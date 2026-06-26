package com.ticketeira.ticket.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Configura o RestClient para chamadas internas ticket-service → event-service.
 * Timeouts: connect 2s / read 3s (conforme ADR-T08).
 * Header X-Internal-Token injetado em todas as requisicoes.
 */
@Configuration
public class EventClientConfig {

    @Bean
    public RestClient restClient(
            @Value("${app.event-service.url}") String eventServiceUrl,
            @Value("${app.internal.token}") String internalToken) {

        ClientHttpRequestFactory factory = ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(Duration.ofSeconds(2))
                        .withReadTimeout(Duration.ofSeconds(3)));

        return RestClient.builder()
                .baseUrl(eventServiceUrl)
                .requestFactory(factory)
                .defaultHeader("X-Internal-Token", internalToken)
                .build();
    }
}
