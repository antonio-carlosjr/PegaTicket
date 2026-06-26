package com.ticketeira.event.repository;

import com.ticketeira.event.domain.Evento;
import com.ticketeira.event.domain.TipoEvento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de concorrencia em POSTGRES REAL (Testcontainers).
 * H2 nao reproduz fielmente o row lock do UPDATE...WHERE sob concorrencia — gate inegociavel.
 *
 * Usa @SpringBootTest (NAO @DataJpaTest): o teste precisa ser NAO-transacional para que o
 * evento semeado seja COMMITADO e visivel as threads worker (conexoes separadas do pool).
 * Com a transacao de rollback do @DataJpaTest, o seed ficava invisivel e todo decremento
 * concorrente afetava 0 linhas (0 sucessos). Mesmo padrao do InscricaoConcorrenciaTest.
 *
 * Pulados automaticamente se Docker nao estiver disponivel (disabledWithoutDocker=true).
 */
@Tag("concorrencia")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test-postgres")
class VagaConcorrenciaTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("event_test")
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
    EventRepository eventRepository;

    private Evento eventoPublicado(int capacidade) {
        Evento e = Evento.criar(1L, "Teste", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local", TipoEvento.GRATUITO, capacidade, null, null, null);
        e.publicar();
        return eventRepository.saveAndFlush(e);
    }

    @BeforeEach
    void limpar() {
        eventRepository.deleteAll();
    }

    // ---- A1: decrementarVaga basico ----

    @Test
    void decrementar_vagas1_retorna1_eVagaFica0() {
        Evento e = eventoPublicado(1);
        int rows = eventRepository.decrementarVaga(e.getId());
        assertThat(rows).isEqualTo(1);
        Evento atualizado = eventRepository.findById(e.getId()).orElseThrow();
        assertThat(atualizado.getVagasDisponiveis()).isEqualTo(0);
    }

    @Test
    void decrementar_vagasZero_retorna0_nunca_negativo() {
        Evento e = eventoPublicado(1);
        eventRepository.decrementarVaga(e.getId()); // consome a unica vaga
        int rows = eventRepository.decrementarVaga(e.getId()); // tenta com vagas=0
        assertThat(rows).isEqualTo(0);
        Evento atualizado = eventRepository.findById(e.getId()).orElseThrow();
        assertThat(atualizado.getVagasDisponiveis()).isGreaterThanOrEqualTo(0);
        assertThat(atualizado.getVagasDisponiveis()).isEqualTo(0);
    }

    @Test
    void decrementar_eventoRascunho_retorna0() {
        Evento e = Evento.criar(1L, "Rascunho", null,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Local", TipoEvento.GRATUITO, 10, null, null, null);
        e = eventRepository.saveAndFlush(e);
        int rows = eventRepository.decrementarVaga(e.getId());
        assertThat(rows).isEqualTo(0);
    }

    @Test
    void decrementar_eventoInexistente_retorna0() {
        int rows = eventRepository.decrementarVaga(99999L);
        assertThat(rows).isEqualTo(0);
    }

    // ---- A2: incrementarVaga basico ----

    @Test
    void incrementar_vaga0_retorna1_eVagaFica1() {
        Evento e = eventoPublicado(10);
        eventRepository.decrementarVaga(e.getId()); // vaga -> 9
        // Zera artificialmente para testar o incremento de 0
        for (int i = 0; i < 9; i++) eventRepository.decrementarVaga(e.getId());
        // vagas=0, capacidade=10
        int rows = eventRepository.incrementarVaga(e.getId());
        assertThat(rows).isEqualTo(1);
        Evento atualizado = eventRepository.findById(e.getId()).orElseThrow();
        assertThat(atualizado.getVagasDisponiveis()).isEqualTo(1);
    }

    @Test
    void incrementar_noTeto_retorna0_naoExcede() {
        Evento e = eventoPublicado(10); // vagas=10=capacidade
        int rows = eventRepository.incrementarVaga(e.getId());
        assertThat(rows).isEqualTo(0);
        Evento atualizado = eventRepository.findById(e.getId()).orElseThrow();
        assertThat(atualizado.getVagasDisponiveis()).isEqualTo(10); // nao excedeu
    }

    // ---- A4: CONCORRENCIA — ultima vaga (K=50 threads) ----

    @RepeatedTest(3) // 3 rodadas para reduzir flakiness
    void concorrencia_ultimaVaga_exatamente1Sucesso() throws Exception {
        final int K = 50;
        Evento e = eventoPublicado(1); // 1 unica vaga
        Long eventoId = e.getId();

        AtomicInteger sucessos = new AtomicInteger(0);
        AtomicInteger falhas = new AtomicInteger(0);
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fim = new CountDownLatch(K);

        ExecutorService pool = Executors.newFixedThreadPool(K);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < K; i++) {
            futures.add(pool.submit(() -> {
                try {
                    inicio.await(); // todos esperam o sinal simultaneo
                    int rows = eventRepository.decrementarVaga(eventoId);
                    if (rows == 1) {
                        sucessos.incrementAndGet();
                    } else {
                        falhas.incrementAndGet();
                    }
                } catch (Exception ex) {
                    falhas.incrementAndGet();
                } finally {
                    fim.countDown();
                }
            }));
        }

        inicio.countDown(); // dispara todas as threads ao mesmo tempo
        fim.await();
        pool.shutdown();

        // Propaga excecoes internas de futures
        for (Future<?> f : futures) f.get();

        assertThat(sucessos.get()).isEqualTo(1);
        assertThat(falhas.get()).isEqualTo(K - 1);

        Evento final_ = eventRepository.findById(eventoId).orElseThrow();
        assertThat(final_.getVagasDisponiveis()).isEqualTo(0);
        assertThat(final_.getVagasDisponiveis()).isGreaterThanOrEqualTo(0);
    }

    // ---- A5: CONCORRENCIA — capacidade N=20 threads K=100 ----

    @RepeatedTest(2)
    void concorrencia_capacidadeN_exatamenteNSucessos() throws Exception {
        final int N = 20; // vagas
        final int K = 100; // threads concorrentes
        Evento e = eventoPublicado(N);
        Long eventoId = e.getId();

        AtomicInteger sucessos = new AtomicInteger(0);
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fim = new CountDownLatch(K);

        ExecutorService pool = Executors.newFixedThreadPool(K);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < K; i++) {
            futures.add(pool.submit(() -> {
                try {
                    inicio.await();
                    int rows = eventRepository.decrementarVaga(eventoId);
                    if (rows == 1) sucessos.incrementAndGet();
                } catch (Exception ex) {
                    // falha: conta como 0
                } finally {
                    fim.countDown();
                }
            }));
        }

        inicio.countDown();
        fim.await();
        pool.shutdown();

        for (Future<?> f : futures) f.get();

        assertThat(sucessos.get()).isEqualTo(N);

        Evento final_ = eventRepository.findById(eventoId).orElseThrow();
        assertThat(final_.getVagasDisponiveis()).isEqualTo(0);
        assertThat(final_.getVagasDisponiveis()).isGreaterThanOrEqualTo(0);
    }
}
