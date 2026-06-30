package com.ticketeira.payment;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base abstrata para testes de integracao do payment-service.
 * Declara Postgres + RabbitMQ como containers static (reuso entre classes — evita subir broker por classe).
 * @DynamicPropertySource injeta as propriedades de conexao em todos os testes filhos.
 *
 * Nota: NAO exclui RabbitAutoConfiguration — esses testes precisam do broker real.
 * Padrao: @Testcontainers(disabledWithoutDocker=true) nas subclasses.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class TestcontainersBase {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("payment_test")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    protected static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer("rabbitmq:3.13-management");

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
