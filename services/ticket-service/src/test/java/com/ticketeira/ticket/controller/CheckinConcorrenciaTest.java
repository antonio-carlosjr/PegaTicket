package com.ticketeira.ticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketeira.ticket.TestcontainersBase;
import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.client.EventResumo;
import com.ticketeira.ticket.domain.Ingresso;
import com.ticketeira.ticket.domain.Inscricao;
import com.ticketeira.ticket.domain.StatusIngresso;
import com.ticketeira.ticket.messaging.InscricaoCanceladaPublisher;
import com.ticketeira.ticket.repository.CheckinRepository;
import com.ticketeira.ticket.repository.IngressoRepository;
import com.ticketeira.ticket.repository.InscricaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A3 — Duplo check-in concorrente (Testcontainers PG, test-postgres). [US-034.6] CRITICO
 * Casos: A3.a
 *
 * Estrategia: UNIQUE(ingresso_id) em checkins + transicao condicional.
 * H2 nao reproduz o row lock — Postgres real (mesmo racional do ADR-T07).
 *
 * VERMELHO: CheckinController, CheckinService, Checkin entity nao existem.
 */
@Tag("concorrencia")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test-postgres")
@DisplayName("A3 — Duplo check-in simultaneo (CRITICO — Testcontainers PG)")
class CheckinConcorrenciaTest extends TestcontainersBase {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    InscricaoRepository inscricaoRepository;

    @Autowired
    IngressoRepository ingressoRepository;

    @Autowired
    CheckinRepository checkinRepository;

    @MockBean
    EventClient eventClient;

    @MockBean
    InscricaoCanceladaPublisher inscricaoCanceladaPublisher;

    private static final Long PROMOTOR_DONO = 1L;
    private static final Long EVENTO_ID = 10L;
    private static final Long PARTICIPANTE_ID = 99L;

    @BeforeEach
    void limpar() {
        checkinRepository.deleteAll();
        ingressoRepository.deleteAll();
        inscricaoRepository.deleteAll();

        when(eventClient.getEvento(anyLong()))
                .thenReturn(new EventResumo(
                        EVENTO_ID, "Show", "GRATUITO", "PUBLICADO",
                        10, 100, null, PROMOTOR_DONO,
                        OffsetDateTime.of(2026, 9, 1, 20, 0, 0, 0, ZoneOffset.of("-03:00")),
                        null
                ));
    }

    // A3.a -------------------------------------------------------------------

    /**
     * A3.a — CRITICO: 2 check-ins simultaneos do mesmo codigo_unico ->
     * exatamente 1 retorna 200 (UTILIZADO) e 1 retorna 409;
     * checkins count=1; ingresso UTILIZADO. [US-034.6]
     */
    @Test
    @DisplayName("A3.a — CRITICO: duplo check-in simultaneo -> 1 sucesso / 1 409 / checkins.count=1")
    void checkin_duploSimultaneo_exatamente1Sucesso() throws Exception {
        // Seed: inscricao + ingresso ATIVO
        Inscricao ins = Inscricao.criar(PARTICIPANTE_ID, EVENTO_ID);
        ins = inscricaoRepository.save(ins);
        Ingresso ing = Ingresso.emitir(ins.getId());
        ing = ingressoRepository.save(ing);
        final String codigo = ing.getCodigoUnico();
        final String requestBody = mapper.writeValueAsString(Map.of("codigoUnico", codigo));

        AtomicInteger sucessos = new AtomicInteger(0);
        AtomicInteger conflitos = new AtomicInteger(0);

        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                try {
                    latch.await();
                    MockHttpServletResponse resp = mvc.perform(post("/tickets/checkin")
                                    .header("X-User-Id", PROMOTOR_DONO)
                                    .header("X-User-Papel", "PROMOTOR")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
                            .andReturn().getResponse();

                    if (resp.getStatus() == 200) {
                        sucessos.incrementAndGet();
                    } else if (resp.getStatus() == 409) {
                        conflitos.incrementAndGet();
                    }
                } catch (Exception e) {
                    conflitos.incrementAndGet();
                }
                return null;
            }));
        }

        latch.countDown();   // dispara as 2 threads simultaneamente

        for (Future<?> f : futures) f.get();
        pool.shutdown();

        // CRITICO: exatamente 1 sucesso e 1 conflito
        assertThat(sucessos.get()).as("Exatamente 1 check-in deve ter sucesso").isEqualTo(1);
        assertThat(conflitos.get()).as("Exatamente 1 check-in deve falhar com 409").isEqualTo(1);

        // CRITICO: apenas 1 registro em checkins
        assertThat(checkinRepository.count()).as("Apenas 1 checkin no banco").isEqualTo(1L);

        // CRITICO: ingresso deve estar UTILIZADO
        Ingresso ingressoFinal = ingressoRepository.findByCodigoUnico(codigo).orElseThrow();
        assertThat(ingressoFinal.getStatus()).isEqualTo(StatusIngresso.UTILIZADO);
    }
}
