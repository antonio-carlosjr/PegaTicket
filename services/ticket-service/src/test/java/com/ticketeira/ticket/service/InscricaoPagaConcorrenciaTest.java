package com.ticketeira.ticket.service;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.client.EventResumo;
import com.ticketeira.ticket.domain.StatusInscricao;
import com.ticketeira.ticket.repository.IngressoRepository;
import com.ticketeira.ticket.repository.InscricaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * A2 — CONCORRENCIA: ultima vaga em evento PAGO.
 * K=10 threads tentam inscrever simultaneamente em evento com 1 vaga.
 * - Exatamente 1 sucesso (PENDENTE_PAGAMENTO criada)
 * - K-1 erros EVENTO_ESGOTADO (409)
 * - vagas_disponiveis=0
 * - Nenhuma das K-1 perdedoras cria Pagamento (nao publica pedido.criado)
 *
 * Usa Postgres REAL (Testcontainers) + RabbitMQ EXCLUIDO (PedidoCriadoPublisher mockado).
 * Espelha InscricaoConcorrenciaTest (S3) com o ramo PAGO.
 * Caso de teste: A2 (tests-spec.md)
 */
@Tag("concorrencia")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test-postgres")
@DisplayName("A2 — Concorrencia ultima vaga em evento PAGO")
class InscricaoPagaConcorrenciaTest {

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
        // Exclui RabbitMQ — testes de concorrencia nao precisam de broker real
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

    // Publisher mockado para capturar/contar publicacoes
    @MockBean
    com.ticketeira.ticket.messaging.PedidoCriadoPublisher pedidoCriadoPublisher;

    private static final Long EVENTO_ID = 200L;
    private static final int K = 10;

    @BeforeEach
    void setUp() {
        ingressoRepository.deleteAll();
        inscricaoRepository.deleteAll();

        // Evento PAGO com 1 vaga
        when(eventClient.getEvento(anyLong()))
                .thenReturn(new EventResumo(EVENTO_ID, "Show Pago", "PAGO", "PUBLICADO", 1, 100,
                        new BigDecimal("150.00"), 5L,
                        java.time.OffsetDateTime.now().plusDays(30), null, 7));

        // reservarVaga: primeira chamada atomica no event-service real (mockado aqui).
        // Simula: 1 thread ganha, demais recebem EVENTO_ESGOTADO (usando estado compartilhado).
        // O estado de "esgotado" e controlado pelo contador de chamadas.
        java.util.concurrent.atomic.AtomicInteger chamadas = new java.util.concurrent.atomic.AtomicInteger(0);
        org.mockito.Mockito.doAnswer(inv -> {
            int n = chamadas.incrementAndGet();
            if (n > 1) {
                throw new BusinessException("EVENTO_ESGOTADO", 409);
            }
            return null;
        }).when(eventClient).reservarVaga(anyLong());

        doNothing().when(eventClient).liberarVaga(anyLong());
        doNothing().when(pedidoCriadoPublisher).publicar(any());
    }

    @RepeatedTest(3)
    @DisplayName("A2 — concorrencia_ultimaVagaPaga_K_threads_1reserva_Kmenos1_409")
    void concorrencia_ultimaVagaPaga_K_threads_1reserva_Kmenos1_409() throws Exception {
        AtomicInteger sucessos = new AtomicInteger(0);
        AtomicInteger esgotados = new AtomicInteger(0);
        AtomicInteger outrosErros = new AtomicInteger(0);
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fim = new CountDownLatch(K);

        ExecutorService pool = Executors.newFixedThreadPool(K);
        List<Future<?>> futures = new ArrayList<>();

        // Cada thread usa um usuarioId diferente (evita conflito de JA_INSCRITO entre threads)
        for (int i = 0; i < K; i++) {
            final Long usuarioId = (long) (1000 + i);
            futures.add(pool.submit(() -> {
                try {
                    inicio.await();
                    inscricaoService.inscrever(EVENTO_ID, usuarioId);
                    sucessos.incrementAndGet();
                } catch (BusinessException ex) {
                    if ("EVENTO_ESGOTADO".equals(ex.getMessage())) {
                        esgotados.incrementAndGet();
                    } else {
                        outrosErros.incrementAndGet();
                    }
                } catch (Exception ex) {
                    outrosErros.incrementAndGet();
                } finally {
                    fim.countDown();
                }
            }));
        }

        inicio.countDown();
        fim.await();
        pool.shutdown();

        for (Future<?> f : futures) f.get();

        // CRITICO: exatamente 1 sucesso, K-1 esgotados
        assertThat(sucessos.get())
                .as("Deve haver exatamente 1 inscricao bem-sucedida")
                .isEqualTo(1);
        assertThat(esgotados.get())
                .as("Deve haver K-1 erros EVENTO_ESGOTADO")
                .isEqualTo(K - 1);
        assertThat(outrosErros.get())
                .as("Nao deve haver outros tipos de erro")
                .isZero();

        // Exatamente 1 inscricao PENDENTE_PAGAMENTO no banco
        long totalInscricoes = inscricaoRepository.count();
        assertThat(totalInscricoes)
                .as("Deve existir exatamente 1 inscricao PENDENTE_PAGAMENTO")
                .isEqualTo(1);

        var inscricao = inscricaoRepository.findAll().get(0);
        assertThat(inscricao.getStatus())
                .as("A inscricao criada deve estar PENDENTE_PAGAMENTO")
                .isEqualTo(StatusInscricao.PENDENTE_PAGAMENTO);

        // CRITICO: ramo PAGO NAO emite ingresso imediato
        assertThat(ingressoRepository.count())
                .as("Nenhum ingresso deve ter sido criado (evento PAGO aguarda pagamento)")
                .isZero();

        // CRITICO: apenas 1 publicacao de pedido.criado (so a thread vencedora)
        org.mockito.Mockito.verify(pedidoCriadoPublisher, org.mockito.Mockito.times(1))
                .publicar(any());
    }
}
