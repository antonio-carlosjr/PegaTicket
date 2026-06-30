package com.ticketeira.ticket;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base abstrata para testes de integracao do ticket-service.
 *
 * Padrao SINGLETON: Postgres + RabbitMQ sao iniciados UMA vez (start manual no bloco
 * static) e reusados por TODAS as classes filhas. NAO usamos @Container aqui de
 * proposito: com @Container static numa base compartilhada por varias classes, o
 * extension do Testcontainers PARA os containers ao fim da primeira classe, deixando a
 * 2a/3a classe sem broker/conexao (listener nao consome -> Awaitility 30s timeout;
 * ou HikariPool total=0). Com start manual sem @Container, os containers ficam vivos
 * pelo JVM inteiro e sao reusados. (Mesma correcao aplicada ao payment no Sprint 4.)
 *
 * @Testcontainers(disabledWithoutDocker=true) (herdado pelas subclasses via extension)
 * pula os testes quando o Docker nao esta disponivel; o start so ocorre se houver Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class TestcontainersBase {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("ticket_test")
                    .withUsername("test")
                    .withPassword("test");

    protected static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer("rabbitmq:3.13-management");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
            RABBITMQ.start();
        }
    }

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        // Postgres
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");

        // RabbitMQ — broker real (NAO excluir RabbitAutoConfiguration nesses testes)
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }
}
