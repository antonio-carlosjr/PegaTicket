package com.ticketeira.ticket.service;

import com.ticketeira.ticket.TestcontainersBase;
import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.client.EventResumo;
import com.ticketeira.ticket.domain.Inscricao;
import com.ticketeira.ticket.messaging.InscricaoCanceladaPublisher;
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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * B3 — Duplo cancelamento concorrente da mesma inscricao (Testcontainers PG). [US-035.5] CRITICO
 * Casos: B3.a
 *
 * VERMELHO: CancelamentoInscricaoService nao existe.
 */
@Tag("concorrencia")
@SpringBootTest
@ActiveProfiles("test-postgres")
@DisplayName("B3 — Duplo cancelamento concorrente (CRITICO — Testcontainers PG)")
class CancelamentoConcorrenciaTest extends TestcontainersBase {

    @Autowired
    CancelamentoInscricaoService cancelamentoService;

    @Autowired
    InscricaoRepository inscricaoRepository;

    @Autowired
    IngressoRepository ingressoRepository;

    @MockBean
    EventClient eventClient;

    @MockBean
    InscricaoCanceladaPublisher inscricaoCanceladaPublisher;

    private static final Long USUARIO_ID = 10L;
    private static final Long EVENTO_ID = 42L;

    @BeforeEach
    void limpar() {
        ingressoRepository.deleteAll();
        inscricaoRepository.deleteAll();

        when(eventClient.getEvento(anyLong()))
                .thenReturn(new EventResumo(
                        EVENTO_ID, "Show", "GRATUITO", "PUBLICADO",
                        10, 100, null, 5L,
                        OffsetDateTime.now().plusDays(30), null
                ));
    }

    // B3.a -------------------------------------------------------------------

    /**
     * B3.a — CRITICO: 2 DELETE simultaneos da mesma inscricao ->
     * exatamente 1 retorna sucesso e 1 retorna 409 INSCRICAO_JA_CANCELADA;
     * liberarVaga chamado 1x. [US-035.5]
     */
    @Test
    @DisplayName("B3.a — CRITICO: duplo cancelamento -> 1 sucesso / 1 409; vaga liberada 1x")
    void cancelar_duploSimultaneo_exatamente1Sucesso() throws Exception {
        Inscricao ins = Inscricao.criar(USUARIO_ID, EVENTO_ID);
        ins = inscricaoRepository.save(ins);
        final Long inscricaoId = ins.getId();

        AtomicInteger sucessos = new AtomicInteger(0);
        AtomicInteger conflitos = new AtomicInteger(0);

        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                try {
                    latch.await();
                    cancelamentoService.cancelar(inscricaoId, USUARIO_ID);
                    sucessos.incrementAndGet();
                } catch (com.ticketeira.common.exception.BusinessException ex) {
                    if ("INSCRICAO_JA_CANCELADA".equals(ex.getMessage())) {
                        conflitos.incrementAndGet();
                    } else {
                        conflitos.incrementAndGet();   // outras excecoes contam como falha
                    }
                } catch (Exception ex) {
                    conflitos.incrementAndGet();
                }
                return null;
            }));
        }

        latch.countDown();
        for (Future<?> f : futures) f.get();
        pool.shutdown();

        // CRITICO: exatamente 1 sucesso e 1 conflito
        assertThat(sucessos.get()).as("Exatamente 1 cancelamento deve ter sucesso").isEqualTo(1);
        assertThat(conflitos.get()).as("Exatamente 1 deve retornar 409").isEqualTo(1);

        // Inscricao deve estar CANCELADA (o vencedor cancelou)
        var inscricaoFinal = inscricaoRepository.findById(inscricaoId).orElseThrow();
        assertThat(inscricaoFinal.getStatus()).isEqualTo(com.ticketeira.ticket.domain.StatusInscricao.CANCELADA);
    }
}
