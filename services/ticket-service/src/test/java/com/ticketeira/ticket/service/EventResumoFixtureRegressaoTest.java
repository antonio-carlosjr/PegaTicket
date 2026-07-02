package com.ticketeira.ticket.service;

import com.ticketeira.ticket.client.EventResumo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F1 — Regressao: EventResumo += dataInicio, dataFim, prazoReembolsoDias. [regressao]
 *
 * Historico: quando o Back implementou a aridade de 10 args (5B, dataInicio+prazoReembolsoDias),
 * TODOS os testes que usavam `new EventResumo(8 args)` quebraram na compilacao — foram
 * atualizados na epoca. Depois, o Back adicionou +dataFim (bloqueio de inscricao em evento
 * ja ENCERRADO), levando a aridade a 11 args. Este arquivo documenta a fixture atual.
 *
 * Padrao atual (11 args):
 *
 *   new EventResumo(id, titulo, tipo, status, vagas, cap, preco, promotorId,
 *                   dataInicio, dataFim, prazoReembolsoDias)
 */
@Tag("regressao")
@DisplayName("F1 — Regressao: EventResumo aridade (11 args) com dataInicio + dataFim + prazoReembolsoDias")
class EventResumoFixtureRegressaoTest {

    private static final OffsetDateTime DATA_FUTURA =
            OffsetDateTime.of(2026, 9, 1, 20, 0, 0, 0, ZoneOffset.of("-03:00"));

    /**
     * Verifica que o record EventResumo aceita os 11 args e expoe os campos dataInicio,
     * dataFim e prazoReembolsoDias.
     */
    @Test
    @DisplayName("EventResumo com 11 args: dataInicio, dataFim e prazoReembolsoDias acessiveis")
    void eventResumo_11args_camposNovosAcessiveis() {
        EventResumo resumo = new EventResumo(
                42L, "Show", "PAGO", "PUBLICADO",
                10, 100, new BigDecimal("100.00"), 5L,
                DATA_FUTURA,               // dataInicio
                DATA_FUTURA.plusHours(3),  // dataFim
                7                          // prazoReembolsoDias
        );

        assertThat(resumo.id()).isEqualTo(42L);
        assertThat(resumo.tipo()).isEqualTo("PAGO");
        assertThat(resumo.dataInicio()).isEqualTo(DATA_FUTURA);
        assertThat(resumo.dataFim()).isEqualTo(DATA_FUTURA.plusHours(3));
        assertThat(resumo.prazoReembolsoDias()).isEqualTo(7);
    }

    /**
     * EventResumo GRATUITO: prazoReembolsoDias = null; dataFim tambem pode ser null
     * (fixtures de caminho-feliz que nao testam a regra de encerramento).
     */
    @Test
    @DisplayName("EventResumo GRATUITO: prazoReembolsoDias=null, dataFim=null")
    void eventResumo_gratuito_prazoNulo() {
        EventResumo resumo = new EventResumo(
                1L, "Workshop", "GRATUITO", "PUBLICADO",
                20, 100, null, 3L,
                OffsetDateTime.now().plusDays(10),
                null,  // dataFim null — nao bloqueia por encerramento
                null   // prazoReembolsoDias null para GRATUITO
        );

        assertThat(resumo.tipo()).isEqualTo("GRATUITO");
        assertThat(resumo.preco()).isNull();
        assertThat(resumo.prazoReembolsoDias()).isNull();
        assertThat(resumo.dataInicio()).isNotNull();
        assertThat(resumo.dataFim()).isNull();
    }
}
