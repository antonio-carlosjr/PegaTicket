package com.ticketeira.event;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base abstrata para testes de integracao do event-service com Postgres + RabbitMQ.
 *
 * Padrao SINGLETON (aprendizado S4): containers iniciados UMA vez no bloco static,
 * guardado por DockerClientFactory.instance().isDockerAvailable(). NAO usa @Container
 * para evitar que o Testcontainers extension pare os containers ao fim da primeira
 * classe filha (causaria HikariPool total=0 na segunda classe).
 *
 * NAO exclui RabbitAutoConfiguration: os testes que estendem esta base precisam do
 * RabbitTemplate real para verificar publicacao de mensagens.
 *
 * @Testcontainers(disabledWithoutDocker=true) pula os testes quando Docker nao esta
 * disponivel (ex.: Windows sem Docker Desktop). Local roda unit/H2; CI roda a saga.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class TestcontainersBase {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("event_test")
                    .withUsername("test")
                    .withPassword("test");

    protected static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer("rabbitmq:3.13-management");

    static {
        // So inicia se Docker existir — disabledWithoutDocker garante que os testes
        // sao pulados antes de chegar aqui quando Docker nao esta disponivel.
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

        // RabbitMQ — broker real; NAO excluir RabbitAutoConfiguration nesses testes
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);

        // Sobrescreve o exclude do application-test-postgres.yml para que o RabbitMQ
        // seja inicializado normalmente nesta base (aprendizado S4: o perfil test-postgres
        // do event-service exclui o Rabbit; aqui precisamos dele).
        registry.add("spring.autoconfigure.exclude", () -> "");
    }
}
