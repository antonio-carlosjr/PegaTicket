package com.ticketeira.ticket.service;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.client.EventResumo;
import com.ticketeira.ticket.domain.Inscricao;
import com.ticketeira.ticket.domain.StatusInscricao;
import com.ticketeira.ticket.messaging.InscricaoCanceladaPublisher;
import com.ticketeira.ticket.repository.IngressoRepository;
import com.ticketeira.ticket.repository.InscricaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B1 — Unit do CancelamentoInscricaoService (mocks de EventClient/publisher/repos). [US-035]
 * Casos: B1.a B1.b B1.c B1.d B1.e B1.f B1.g
 *
 * EventClient.getEvento() mockado para controlar tipo/dataInicio/prazoReembolsoDias.
 *
 * VERMELHO: CancelamentoInscricaoService nao existe.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("B1 — CancelamentoInscricaoService regras de negocio (US-035)")
class CancelamentoInscricaoServiceTest {

    @Mock
    InscricaoRepository inscricaoRepository;

    @Mock
    IngressoRepository ingressoRepository;

    @Mock
    EventClient eventClient;

    @Mock
    InscricaoCanceladaPublisher inscricaoCanceladaPublisher;

    CancelamentoInscricaoService cancelamentoService;

    private static final Long USUARIO_ID = 10L;
    private static final Long EVENTO_ID = 42L;
    private static final Long INSCRICAO_ID = 1L;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        TransactionStatus txStatus = mock(TransactionStatus.class);
        when(txManager.getTransaction(any())).thenReturn(txStatus);

        cancelamentoService = new CancelamentoInscricaoService(
                inscricaoRepository, ingressoRepository, eventClient,
                inscricaoCanceladaPublisher, txManager);
    }

    private Inscricao inscricaoAtiva(Long id, Long usuarioId, Long eventoId) {
        Inscricao ins = Inscricao.criar(usuarioId, eventoId);
        try {
            var campo = Inscricao.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(ins, id);
        } catch (Exception e) { throw new RuntimeException(e); }
        return ins;
    }

    private Inscricao inscricaoCancelada(Long id, Long usuarioId, Long eventoId) {
        Inscricao ins = inscricaoAtiva(id, usuarioId, eventoId);
        ins.cancelarPorEvento();   // ja CANCELADA
        return ins;
    }

    /** EventResumo GRATUITO simples (sem prazo). */
    private EventResumo eventoGratuito() {
        return new EventResumo(
                EVENTO_ID, "Workshop", "GRATUITO", "PUBLICADO",
                10, 100, null, 5L,
                OffsetDateTime.now().plusDays(30),
                null,
                null
        );
    }

    /** EventResumo PAGO com data e prazo. Dentro do prazo se now <= dataInicio - prazo. */
    private EventResumo eventoPagoDentroDoFrazo() {
        // dataInicio = 30 dias no futuro; prazo = 7 dias -> ainda ha 23 dias de prazo
        return new EventResumo(
                EVENTO_ID, "Show", "PAGO", "PUBLICADO",
                10, 100, new BigDecimal("100.00"), 5L,
                OffsetDateTime.now().plusDays(30),
                null,
                7
        );
    }

    /** EventResumo PAGO fora do prazo (dataInicio ja passou). */
    private EventResumo eventoPagoForaDoPrazo() {
        // dataInicio = ontem; prazo = 7 -> fora do prazo
        return new EventResumo(
                EVENTO_ID, "Show Passado", "PAGO", "PUBLICADO",
                10, 100, new BigDecimal("100.00"), 5L,
                OffsetDateTime.now().minusDays(1),
                null,
                7
        );
    }

    // B1.a -------------------------------------------------------------------

    /**
     * B1.a — evento GRATUITO: cancela -> CANCELADA + liberarVaga; NAO publica; reembolsoIniciado=false. [US-035.1]
     */
    @Test
    @DisplayName("B1.a — GRATUITO: cancela sem publicar inscricao.cancelada")
    void cancelar_eventoGratuito_cancelaSemPublicar() {
        Inscricao ins = inscricaoAtiva(INSCRICAO_ID, USUARIO_ID, EVENTO_ID);
        when(inscricaoRepository.findById(INSCRICAO_ID)).thenReturn(Optional.of(ins));
        when(inscricaoRepository.cancelarPorParticipante(INSCRICAO_ID)).thenReturn(1);
        when(eventClient.getEvento(EVENTO_ID)).thenReturn(eventoGratuito());

        var resp = cancelamentoService.cancelar(INSCRICAO_ID, USUARIO_ID);

        // Nao deve publicar para evento GRATUITO
        verify(inscricaoCanceladaPublisher, never()).publicar(any());
        // liberarVaga deve ser chamado
        verify(eventClient).liberarVaga(EVENTO_ID);
        // reembolsoIniciado = false
        org.assertj.core.api.Assertions.assertThat(resp.reembolsoIniciado()).isFalse();
        org.assertj.core.api.Assertions.assertThat(resp.status()).isEqualTo("CANCELADA");
    }

    // B1.b -------------------------------------------------------------------

    /**
     * B1.b — evento PAGO dentro do prazo: cancela + publica inscricao.cancelada; reembolsoIniciado=true. [US-035.2]
     */
    @Test
    @DisplayName("B1.b — PAGO dentro do prazo: cancela + publica + reembolsoIniciado=true")
    void cancelar_eventoPagoDentroDoFrazo_publicaEReembolso() {
        Inscricao ins = inscricaoAtiva(INSCRICAO_ID, USUARIO_ID, EVENTO_ID);
        when(inscricaoRepository.findById(INSCRICAO_ID)).thenReturn(Optional.of(ins));
        when(inscricaoRepository.cancelarPorParticipante(INSCRICAO_ID)).thenReturn(1);
        when(eventClient.getEvento(EVENTO_ID)).thenReturn(eventoPagoDentroDoFrazo());

        var resp = cancelamentoService.cancelar(INSCRICAO_ID, USUARIO_ID);

        verify(inscricaoCanceladaPublisher).publicar(any());
        org.assertj.core.api.Assertions.assertThat(resp.reembolsoIniciado()).isTrue();
        org.assertj.core.api.Assertions.assertThat(resp.status()).isEqualTo("CANCELADA");
    }

    // B1.c -------------------------------------------------------------------

    /**
     * B1.c — evento PAGO fora do prazo: lanca 422 PRAZO_CANCELAMENTO_ENCERRADO;
     * nada publicado; liberarVaga NAO chamado. [US-035.3 / PO-D2]
     */
    @Test
    @DisplayName("B1.c — PAGO fora do prazo -> 422; inscricao permanece ATIVA; sem publish")
    void cancelar_eventoPagoForaDoPrazo_lanca422() {
        Inscricao ins = inscricaoAtiva(INSCRICAO_ID, USUARIO_ID, EVENTO_ID);
        when(inscricaoRepository.findById(INSCRICAO_ID)).thenReturn(Optional.of(ins));
        when(eventClient.getEvento(EVENTO_ID)).thenReturn(eventoPagoForaDoPrazo());

        assertThatThrownBy(() -> cancelamentoService.cancelar(INSCRICAO_ID, USUARIO_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("PRAZO_CANCELAMENTO_ENCERRADO")
                .extracting("status").isEqualTo(422);

        verify(inscricaoCanceladaPublisher, never()).publicar(any());
        verify(eventClient, never()).liberarVaga(anyLong());
        verify(inscricaoRepository, never()).cancelarPorParticipante(anyLong());
    }

    // B1.d -------------------------------------------------------------------

    /**
     * B1.d — inscricao de outro usuario -> 403 CANCELAMENTO_DE_OUTRO; sem mutacao. [US-035.4]
     */
    @Test
    @DisplayName("B1.d — inscricao de outro usuario -> 403 CANCELAMENTO_DE_OUTRO")
    void cancelar_inscricaoDeOutro_lanca403() {
        Long outroUsuario = 999L;
        Inscricao ins = inscricaoAtiva(INSCRICAO_ID, outroUsuario, EVENTO_ID);
        when(inscricaoRepository.findById(INSCRICAO_ID)).thenReturn(Optional.of(ins));

        assertThatThrownBy(() -> cancelamentoService.cancelar(INSCRICAO_ID, USUARIO_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("CANCELAMENTO_DE_OUTRO")
                .extracting("status").isEqualTo(403);

        verify(inscricaoCanceladaPublisher, never()).publicar(any());
    }

    // B1.e -------------------------------------------------------------------

    /**
     * B1.e — inscricao ja CANCELADA -> 409 INSCRICAO_JA_CANCELADA (rowsAffected=0). [US-035.5]
     */
    @Test
    @DisplayName("B1.e — inscricao ja CANCELADA -> 409 INSCRICAO_JA_CANCELADA")
    void cancelar_inscricaoJaCancelada_lanca409() {
        Inscricao ins = inscricaoAtiva(INSCRICAO_ID, USUARIO_ID, EVENTO_ID);
        when(inscricaoRepository.findById(INSCRICAO_ID)).thenReturn(Optional.of(ins));
        when(inscricaoRepository.cancelarPorParticipante(INSCRICAO_ID)).thenReturn(0);   // rowsAffected=0
        when(eventClient.getEvento(EVENTO_ID)).thenReturn(eventoGratuito());

        assertThatThrownBy(() -> cancelamentoService.cancelar(INSCRICAO_ID, USUARIO_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("INSCRICAO_JA_CANCELADA")
                .extracting("status").isEqualTo(409);
    }

    // B1.f -------------------------------------------------------------------

    /**
     * B1.f — inscricao inexistente -> 404 INSCRICAO_NAO_ENCONTRADA. [borda]
     */
    @Test
    @DisplayName("B1.f — inscricao inexistente -> 404 INSCRICAO_NAO_ENCONTRADA")
    void cancelar_inscricaoInexistente_lanca404() {
        when(inscricaoRepository.findById(INSCRICAO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cancelamentoService.cancelar(INSCRICAO_ID, USUARIO_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("INSCRICAO_NAO_ENCONTRADA")
                .extracting("status").isEqualTo(404);
    }

    // B1.g -------------------------------------------------------------------

    /**
     * B1.g — borda do prazo: limite exato (dataInicio - prazo == now) -> dentro do prazo (publica).
     *         dataInicio ja passou -> fora do prazo (422). [US-035.3]
     */
    @Test
    @DisplayName("B1.g — borda do prazo: limite exato = dentro; dataInicio passado = fora")
    void cancelar_bordaDoPrazo() {
        // Caso 1: dataInicio = prazo dias no futuro exato => limite = now => dentro do prazo
        EventResumo eventoLimiteExato = new EventResumo(
                EVENTO_ID, "Show Limite", "PAGO", "PUBLICADO",
                10, 100, new BigDecimal("100.00"), 5L,
                OffsetDateTime.now().plusDays(7).plusMinutes(1),   // levemente alem do prazo
                null,
                7
        );

        Inscricao ins1 = inscricaoAtiva(INSCRICAO_ID, USUARIO_ID, EVENTO_ID);
        when(inscricaoRepository.findById(INSCRICAO_ID)).thenReturn(Optional.of(ins1));
        when(inscricaoRepository.cancelarPorParticipante(INSCRICAO_ID)).thenReturn(1);
        when(eventClient.getEvento(EVENTO_ID)).thenReturn(eventoLimiteExato);

        var resp = cancelamentoService.cancelar(INSCRICAO_ID, USUARIO_ID);
        org.assertj.core.api.Assertions.assertThat(resp.reembolsoIniciado()).isTrue();
    }

    @Test
    @DisplayName("B1.g (variante) — dataInicio passado -> 422 PRAZO_CANCELAMENTO_ENCERRADO")
    void cancelar_dataInicioPaasado_lanca422() {
        Inscricao ins = inscricaoAtiva(INSCRICAO_ID, USUARIO_ID, EVENTO_ID);
        when(inscricaoRepository.findById(INSCRICAO_ID)).thenReturn(Optional.of(ins));
        when(eventClient.getEvento(EVENTO_ID)).thenReturn(eventoPagoForaDoPrazo());

        assertThatThrownBy(() -> cancelamentoService.cancelar(INSCRICAO_ID, USUARIO_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("PRAZO_CANCELAMENTO_ENCERRADO");
    }
}
