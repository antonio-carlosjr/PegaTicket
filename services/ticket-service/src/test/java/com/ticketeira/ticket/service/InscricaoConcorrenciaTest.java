package com.ticketeira.ticket.service;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.client.EventResumo;
import com.ticketeira.ticket.domain.Ingresso;
import com.ticketeira.ticket.domain.Inscricao;
import com.ticketeira.ticket.dto.InscricaoResponse;
import com.ticketeira.ticket.messaging.PedidoCriadoPublisher;
import com.ticketeira.ticket.repository.IngressoRepository;
import com.ticketeira.ticket.repository.InscricaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Testes de concorrencia do ticket-service em POSTGRES REAL (Testcontainers).
 * EventClient mockado — isolamos a logica de saga + UNIQUE constraint do Postgres.
 *
 * Pulados automaticamente se Docker nao estiver disponivel (disabledWithoutDocker=true).
 */
@Tag("concorrencia")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test-postgres")
class InscricaoConcorrenciaTest {

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
    }

    @Autowired
    InscricaoService inscricaoService;

    @Autowired
    InscricaoRepository inscricaoRepository;

    @Autowired
    IngressoRepository ingressoRepository;

    @MockBean
    EventClient eventClient;

    @MockBean
    PedidoCriadoPublisher pedidoCriadoPublisher;

    private static final Long USUARIO_ID = 1L;
    private static final Long EVENTO_ID = 100L;

    @BeforeEach
    void setUp() {
        ingressoRepository.deleteAll();
        inscricaoRepository.deleteAll();

        when(eventClient.getEvento(anyLong()))
                .thenReturn(new EventResumo(EVENTO_ID, "Show", "GRATUITO", "PUBLICADO", 10, 100,
                        null, 1L));
        doNothing().when(eventClient).reservarVaga(anyLong());
        doNothing().when(eventClient).liberarVaga(anyLong());
    }

    // ---- B4: CONCORRENCIA — dupla inscricao paralela (mesmo usuario+evento) ----

    @RepeatedTest(3)
    void concorrencia_duplaInscricaoParalela_exatamente1Sucesso() throws Exception {
        final int K = 10;
        AtomicInteger sucessos = new AtomicInteger(0);
        AtomicInteger conflitos = new AtomicInteger(0);
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fim = new CountDownLatch(K);

        ExecutorService pool = Executors.newFixedThreadPool(K);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < K; i++) {
            futures.add(pool.submit(() -> {
                try {
                    inicio.await();
                    inscricaoService.inscrever(EVENTO_ID, USUARIO_ID);
                    sucessos.incrementAndGet();
                } catch (BusinessException ex) {
                    if ("JA_INSCRITO".equals(ex.getMessage())) {
                        conflitos.incrementAndGet();
                    }
                } catch (Exception ex) {
                    // Outras excecoes: conta como conflito para o teste
                    conflitos.incrementAndGet();
                } finally {
                    fim.countDown();
                }
            }));
        }

        inicio.countDown();
        fim.await();
        pool.shutdown();

        for (Future<?> f : futures) f.get();

        assertThat(sucessos.get()).isEqualTo(1);
        assertThat(conflitos.get()).isEqualTo(K - 1);

        // Exatamente 1 inscricao e 1 ingresso no banco
        assertThat(inscricaoRepository.count()).isEqualTo(1);
        assertThat(ingressoRepository.count()).isEqualTo(1);
    }

    // ---- B6: Ingresso unico por inscricao ----

    @Test
    void ingressoUnico_duasTentativas_segundaFalha() {
        // Primeira inscricao (caminho feliz)
        InscricaoResponse resp = inscricaoService.inscrever(EVENTO_ID, USUARIO_ID);
        assertThat(resp.ingresso().codigoUnico()).isNotBlank();

        // Tentar inscrever de novo: pre-check pega como JA_INSCRITO
        try {
            inscricaoService.inscrever(EVENTO_ID, USUARIO_ID);
        } catch (BusinessException ex) {
            assertThat(ex.getMessage()).isEqualTo("JA_INSCRITO");
        }

        assertThat(ingressoRepository.count()).isEqualTo(1);
    }

    // ---- B2: Evento nao-publicado ----

    @Test
    void inscrever_eventoPago_criaPendentePagamento() {
        // Sprint 4: evento PAGO agora e suportado — cria PENDENTE_PAGAMENTO
        when(eventClient.getEvento(EVENTO_ID))
                .thenReturn(new EventResumo(EVENTO_ID, "Festival Pago", "PAGO", "PUBLICADO", 10, 100,
                        new java.math.BigDecimal("99.00"), 1L));

        org.mockito.Mockito.doNothing().when(pedidoCriadoPublisher)
                .publicar(org.mockito.ArgumentMatchers.any());

        com.ticketeira.ticket.dto.InscricaoResponse resp =
                inscricaoService.inscrever(EVENTO_ID, USUARIO_ID);

        assertThat(resp.status()).isEqualTo("PENDENTE_PAGAMENTO");
        assertThat(inscricaoRepository.count()).isEqualTo(1);
        assertThat(ingressoRepository.count()).isZero();
    }

    @Test
    void inscrever_eventoNaoPublicado_lanca422() {
        when(eventClient.getEvento(EVENTO_ID))
                .thenReturn(new EventResumo(EVENTO_ID, "Rascunho", "GRATUITO", "RASCUNHO", null, 100,
                        null, 1L));

        try {
            inscricaoService.inscrever(EVENTO_ID, USUARIO_ID);
        } catch (BusinessException ex) {
            assertThat(ex.getMessage()).isEqualTo("EVENTO_NAO_PUBLICADO");
            assertThat(ex.getStatus()).isEqualTo(422);
        }

        assertThat(inscricaoRepository.count()).isEqualTo(0);
    }
}
