package com.ticketeira.ticket.service;

import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.client.EventResumo;
import com.ticketeira.ticket.domain.Inscricao;
import com.ticketeira.ticket.domain.StatusInscricao;
import com.ticketeira.ticket.messaging.PedidoCriadoPublisher;
import com.ticketeira.ticket.repository.IngressoRepository;
import com.ticketeira.ticket.repository.InscricaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes do ExpiracaoReservaJob (A4).
 * Usa Postgres REAL (Testcontainers) — indice parcial idx_inscricoes_pendentes deve funcionar.
 * RabbitMQ excluido — job nao publica eventos.
 * Cobre:
 * - A4.a: expiracaoJob_pendenteVencida_expiraEliberaVaga
 * - A4.b: expiracaoJob_pendenteRecente_naoExpira
 * - A4.c: expiracaoJob_idempotente_inscricaoJaAtiva_naoToca
 * Casos de teste: A4 (tests-spec.md)
 */
@Tag("integracao")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test-postgres")
@DisplayName("A4 — ExpiracaoReservaJob (TTL de reserva)")
class ExpiracaoReservaJobTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ticket_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration");
        // TTL de 30 minutos (padrao)
        registry.add("app.reserva.ttl-min", () -> "30");
    }

    @Autowired
    ExpiracaoReservaJob expiracaoReservaJob;

    @Autowired
    InscricaoRepository inscricaoRepository;

    @Autowired
    IngressoRepository ingressoRepository;

    @MockBean
    EventClient eventClient;

    @MockBean
    PedidoCriadoPublisher pedidoCriadoPublisher;

    @BeforeEach
    void limpar() {
        ingressoRepository.deleteAll();
        inscricaoRepository.deleteAll();

        when(eventClient.getEvento(anyLong()))
                .thenReturn(new EventResumo(1L, "Show", "PAGO", "PUBLICADO", 10, 100,
                        new BigDecimal("100.00"), 5L,
                        java.time.OffsetDateTime.now().plusDays(30), 7));
    }

    @Test
    @DisplayName("A4.a — expiracaoJob_pendenteVencida_expiraEliberaVaga")
    void expiracaoJob_pendenteVencida_expiraEliberaVaga() {
        // Cria inscricao PENDENTE_PAGAMENTO com inscritoEm = now - 31 min (vencida)
        Inscricao inscricao = Inscricao.pendentePagamento(10L, 100L);
        setInscritoEm(inscricao, OffsetDateTime.now().minusMinutes(31));
        inscricaoRepository.saveAndFlush(inscricao);

        expiracaoReservaJob.executar();

        // Status deve ser EXPIRADA
        var atualizada = inscricaoRepository.findById(inscricao.getId()).orElseThrow();
        assertThat(atualizada.getStatus())
                .as("Inscricao vencida deve estar EXPIRADA")
                .isEqualTo(StatusInscricao.EXPIRADA);

        // liberarVaga deve ter sido chamado 1x
        verify(eventClient, times(1)).liberarVaga(100L);
    }

    @Test
    @DisplayName("A4.b — expiracaoJob_pendenteRecente_naoExpira")
    void expiracaoJob_pendenteRecente_naoExpira() {
        // Cria inscricao PENDENTE_PAGAMENTO com inscritoEm = now - 5 min (dentro do TTL)
        Inscricao inscricao = Inscricao.pendentePagamento(11L, 101L);
        setInscritoEm(inscricao, OffsetDateTime.now().minusMinutes(5));
        inscricaoRepository.saveAndFlush(inscricao);

        expiracaoReservaJob.executar();

        // Status deve permanecer PENDENTE_PAGAMENTO
        var atualizada = inscricaoRepository.findById(inscricao.getId()).orElseThrow();
        assertThat(atualizada.getStatus())
                .as("Inscricao recente deve permanecer PENDENTE_PAGAMENTO")
                .isEqualTo(StatusInscricao.PENDENTE_PAGAMENTO);

        // liberarVaga NAO deve ser chamado
        verify(eventClient, never()).liberarVaga(anyLong());
    }

    @Test
    @DisplayName("A4.c — expiracaoJob_idempotente_inscricaoJaAtiva_naoToca")
    void expiracaoJob_idempotente_inscricaoJaAtiva_naoToca() {
        // Cria inscricao ATIVA (nao deve ser tocada pelo job)
        Inscricao inscricaoAtiva = Inscricao.criar(12L, 102L);
        setInscritoEm(inscricaoAtiva, OffsetDateTime.now().minusMinutes(60));
        inscricaoRepository.saveAndFlush(inscricaoAtiva);

        expiracaoReservaJob.executar();

        // Status permanece ATIVA
        var atualizada = inscricaoRepository.findById(inscricaoAtiva.getId()).orElseThrow();
        assertThat(atualizada.getStatus())
                .as("Inscricao ATIVA nao deve ser tocada pelo job de expiracao")
                .isEqualTo(StatusInscricao.ATIVA);

        verify(eventClient, never()).liberarVaga(anyLong());
    }

    @Test
    @DisplayName("A4.d — expiracaoJob_misturado_expiraApenasVencidas")
    void expiracaoJob_misturado_expiraApenasVencidas() {
        // Vencida (deve expirar)
        Inscricao vencida = Inscricao.pendentePagamento(20L, 200L);
        setInscritoEm(vencida, OffsetDateTime.now().minusMinutes(35));
        inscricaoRepository.saveAndFlush(vencida);

        // Recente (nao deve expirar)
        Inscricao recente = Inscricao.pendentePagamento(21L, 201L);
        setInscritoEm(recente, OffsetDateTime.now().minusMinutes(10));
        inscricaoRepository.saveAndFlush(recente);

        expiracaoReservaJob.executar();

        var vencidaAtualizada = inscricaoRepository.findById(vencida.getId()).orElseThrow();
        var resenteAtualizada = inscricaoRepository.findById(recente.getId()).orElseThrow();

        assertThat(vencidaAtualizada.getStatus()).isEqualTo(StatusInscricao.EXPIRADA);
        assertThat(resenteAtualizada.getStatus()).isEqualTo(StatusInscricao.PENDENTE_PAGAMENTO);

        // liberarVaga chamado apenas para a vencida (eventoId=200)
        verify(eventClient, times(1)).liberarVaga(200L);
        verify(eventClient, never()).liberarVaga(201L);
    }

    // Helper: seta inscritoEm via reflexao (campo privado)
    private void setInscritoEm(Inscricao inscricao, OffsetDateTime valor) {
        try {
            var campo = Inscricao.class.getDeclaredField("inscritoEm");
            campo.setAccessible(true);
            campo.set(inscricao, valor);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
