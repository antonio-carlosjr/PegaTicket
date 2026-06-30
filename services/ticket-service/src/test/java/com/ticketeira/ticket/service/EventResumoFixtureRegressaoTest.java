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
 * F1 — Regressao: EventResumo += dataInicio, prazoReembolsoDias. [regressao]
 *
 * Quando o Back implementar a nova aridade de EventResumo (10 args),
 * TODOS os testes existentes que usam `new EventResumo(8 args)` quebrarao
 * na compilacao. Este arquivo documenta a fixture atualizada e serve como
 * referencia para o Back atualizar as invocacoes nos testes S4:
 *
 * Arquivos afetados (substituir 8 args por 10 args):
 * - InscricaoServiceTest.java (3 ocorrencias)
 * - InscricaoPagaServiceTest.java (4 ocorrencias)
 * - InscricaoAfterCommitRollbackTest.java (1 ocorrencia)
 * - InternalEventAuthTest.java (1 ocorrencia)
 * - InscricaoConcorrenciaTest.java (3 ocorrencias)
 * - InscricaoPagaConcorrenciaTest.java (1 ocorrencia)
 * - TicketControllerIntegrationTest.java (2 ocorrencias)
 * - PagamentoAprovadoListenerIntegrationTest.java (1 ocorrencia)
 * - ExpiracaoReservaJobTest.java (1 ocorrencia)
 *
 * Padrao de substituicao — adicionar os 2 ultimos args em todas as instancias:
 *
 *   // ANTES (S4):
 *   new EventResumo(id, titulo, tipo, status, vagas, cap, preco, promotorId)
 *
 *   // DEPOIS (5B):
 *   new EventResumo(id, titulo, tipo, status, vagas, cap, preco, promotorId,
 *                   OffsetDateTime.now().plusDays(30),    // dataInicio
 *                   7)                                    // prazoReembolsoDias (null se GRATUITO)
 *
 * VERMELHO: EventResumo ainda nao tem os 2 campos novos; este teste falha na compilacao
 * ate o Back adicionar os campos.
 */
@Tag("regressao")
@DisplayName("F1 — Regressao: EventResumo nova aridade (10 args) com dataInicio + prazoReembolsoDias")
class EventResumoFixtureRegressaoTest {

    private static final OffsetDateTime DATA_FUTURA =
            OffsetDateTime.of(2026, 9, 1, 20, 0, 0, 0, ZoneOffset.of("-03:00"));

    /**
     * Verifica que o record EventResumo aceita os 10 args e expoe os 2 novos campos.
     * VERMELHO ate o Back adicionar dataInicio+prazoReembolsoDias ao record.
     */
    @Test
    @DisplayName("EventResumo com 10 args: dataInicio e prazoReembolsoDias acessiveis")
    void eventResumo_10args_camposNovosAcessiveis() {
        EventResumo resumo = new EventResumo(
                42L, "Show", "PAGO", "PUBLICADO",
                10, 100, new BigDecimal("100.00"), 5L,
                DATA_FUTURA,    // dataInicio — NOVO
                7               // prazoReembolsoDias — NOVO
        );

        assertThat(resumo.id()).isEqualTo(42L);
        assertThat(resumo.tipo()).isEqualTo("PAGO");
        assertThat(resumo.dataInicio()).isEqualTo(DATA_FUTURA);
        assertThat(resumo.prazoReembolsoDias()).isEqualTo(7);
    }

    /**
     * EventResumo GRATUITO: prazoReembolsoDias = null.
     */
    @Test
    @DisplayName("EventResumo GRATUITO: prazoReembolsoDias=null")
    void eventResumo_gratuito_prazoNulo() {
        EventResumo resumo = new EventResumo(
                1L, "Workshop", "GRATUITO", "PUBLICADO",
                20, 100, null, 3L,
                OffsetDateTime.now().plusDays(10),
                null   // prazoReembolsoDias null para GRATUITO
        );

        assertThat(resumo.tipo()).isEqualTo("GRATUITO");
        assertThat(resumo.preco()).isNull();
        assertThat(resumo.prazoReembolsoDias()).isNull();
        assertThat(resumo.dataInicio()).isNotNull();
    }
}
