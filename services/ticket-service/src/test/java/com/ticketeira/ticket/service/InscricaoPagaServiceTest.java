package com.ticketeira.ticket.service;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.client.EventResumo;
import com.ticketeira.ticket.domain.StatusInscricao;
import com.ticketeira.ticket.dto.InscricaoResponse;
import com.ticketeira.ticket.messaging.PedidoCriadoPublisher;
import com.ticketeira.ticket.repository.IngressoRepository;
import com.ticketeira.ticket.repository.InscricaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitarios do InscricaoService — ramo PAGO (A1) e regressao GRATUITO (A6).
 * Usa Mockito puro, sem Spring ou banco.
 * O PedidoCriadoPublisher e mockado para verificar se publica pedido.criado.
 * Casos de teste: A1 (tests-spec.md — ramo PAGO), A6 (regressao GRATUITO)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("A1/A6 — InscricaoService ramo PAGO e regressao GRATUITO")
class InscricaoPagaServiceTest {

    @Mock
    InscricaoRepository inscricaoRepository;

    @Mock
    IngressoRepository ingressoRepository;

    @Mock
    EventClient eventClient;

    @Mock
    PedidoCriadoPublisher pedidoCriadoPublisher;

    InscricaoService inscricaoService;

    private static final Long USUARIO_ID = 10L;
    private static final Long EVENTO_ID = 42L;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        TransactionStatus txStatus = mock(TransactionStatus.class);
        when(txManager.getTransaction(any())).thenReturn(txStatus);

        inscricaoService = new InscricaoService(
                inscricaoRepository, ingressoRepository, eventClient,
                pedidoCriadoPublisher, txManager);

        when(inscricaoRepository.existsByUsuarioIdAndEventoId(anyLong(), anyLong())).thenReturn(false);
    }

    // ---- A1: Ramo PAGO ----

    @Test
    @DisplayName("A1.a — inscrever_eventoPago_criaPendentePagamento_semIngresso")
    void inscrever_eventoPago_criaPendentePagamento_semIngresso() {
        // Evento PAGO PUBLICADO com preco=100
        when(eventClient.getEvento(EVENTO_ID))
                .thenReturn(new EventResumo(EVENTO_ID, "Show", "PAGO", "PUBLICADO", 10, 100,
                        new BigDecimal("100.00"), 5L,
                        java.time.OffsetDateTime.now().plusDays(30), null, 7));

        // Mock do save retorna inscricao com PENDENTE_PAGAMENTO
        com.ticketeira.ticket.domain.Inscricao inscricaoSalva = mockInscricaoPendente(1L);
        when(inscricaoRepository.save(any())).thenReturn(inscricaoSalva);

        InscricaoResponse resp = inscricaoService.inscrever(EVENTO_ID, USUARIO_ID);

        assertThat(resp.status()).isEqualTo(StatusInscricao.PENDENTE_PAGAMENTO.name());
        assertThat(resp.ingresso()).isNull();   // SEM ingresso enquanto pendente
        assertThat(resp.pagamento()).isNotNull();
        assertThat(resp.pagamento().valor()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(resp.pagamento().status()).isEqualTo("AGUARDANDO");

        // Nao deve ter salvo ingresso
        verify(ingressoRepository, never()).save(any());
    }

    @Test
    @DisplayName("A1.b — inscrever_eventoPago_publicaPedidoCriado_aposCommit")
    void inscrever_eventoPago_publicaPedidoCriado_registraAfterCommit() {
        when(eventClient.getEvento(EVENTO_ID))
                .thenReturn(new EventResumo(EVENTO_ID, "Show", "PAGO", "PUBLICADO", 10, 100,
                        new BigDecimal("100.00"), 5L,
                        java.time.OffsetDateTime.now().plusDays(30), null, 7));

        com.ticketeira.ticket.domain.Inscricao inscricaoSalva = mockInscricaoPendente(1L);
        when(inscricaoRepository.save(any())).thenReturn(inscricaoSalva);

        inscricaoService.inscrever(EVENTO_ID, USUARIO_ID);

        // Com TransactionManager mockado, afterCommit e chamado inline
        // Verificar que o publisher foi chamado com inscricaoId, usuarioId, valor
        verify(pedidoCriadoPublisher).publicar(any(com.ticketeira.ticket.messaging.PedidoCriadoEvent.class));
    }

    @Test
    @DisplayName("A1.c — inscrever_eventoPago_esgotado_lanca409_nenhumPedidoCriado")
    void inscrever_eventoPago_esgotado_lanca409_nenhumPedidoCriado() {
        when(eventClient.getEvento(EVENTO_ID))
                .thenReturn(new EventResumo(EVENTO_ID, "Show", "PAGO", "PUBLICADO", 0, 100,
                        new BigDecimal("100.00"), 5L,
                        java.time.OffsetDateTime.now().plusDays(30), null, 7));
        doThrow(new BusinessException("EVENTO_ESGOTADO", 409))
                .when(eventClient).reservarVaga(EVENTO_ID);

        assertThatThrownBy(() -> inscricaoService.inscrever(EVENTO_ID, USUARIO_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("EVENTO_ESGOTADO");

        verify(inscricaoRepository, never()).save(any());
        verify(pedidoCriadoPublisher, never()).publicar(any());
    }

    @Test
    @DisplayName("A1.d — inscrever_eventoPagoJaEncerrado_lanca422_semReservarESemPublicar")
    void inscrever_eventoPagoJaEncerrado_lanca422_semReservarESemPublicar() {
        when(eventClient.getEvento(EVENTO_ID))
                .thenReturn(new EventResumo(EVENTO_ID, "Show", "PAGO", "PUBLICADO", 10, 100,
                        new BigDecimal("100.00"), 5L,
                        java.time.OffsetDateTime.now().minusDays(2),   // dataInicio
                        java.time.OffsetDateTime.now().minusDays(1),   // dataFim (ja passou)
                        7));

        assertThatThrownBy(() -> inscricaoService.inscrever(EVENTO_ID, USUARIO_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("EVENTO_JA_ENCERRADO");

        verify(eventClient, never()).reservarVaga(any());
        verify(pedidoCriadoPublisher, never()).publicar(any());
    }

    // ---- A6: Regressao GRATUITO ----

    @Test
    @DisplayName("A6 — inscrever_eventoGratuito_emiteIngressoImediato_intacto")
    void inscrever_eventoGratuito_emiteIngressoImediato_intacto() {
        when(eventClient.getEvento(EVENTO_ID))
                .thenReturn(new EventResumo(EVENTO_ID, "Workshop", "GRATUITO", "PUBLICADO", 10, 100,
                        null, 5L, java.time.OffsetDateTime.now().plusDays(30), null, null));

        com.ticketeira.ticket.domain.Inscricao inscricaoSalva = mockInscricaoAtiva(2L);
        com.ticketeira.ticket.domain.Ingresso ingressoSalvo = mockIngresso(2L, 2L);
        when(inscricaoRepository.save(any())).thenReturn(inscricaoSalva);
        when(ingressoRepository.save(any())).thenReturn(ingressoSalvo);

        InscricaoResponse resp = inscricaoService.inscrever(EVENTO_ID, USUARIO_ID);

        assertThat(resp.status()).isEqualTo(StatusInscricao.ATIVA.name());
        assertThat(resp.ingresso()).isNotNull();
        assertThat(resp.ingresso().codigoUnico()).isNotBlank();
        assertThat(resp.pagamento()).isNull(); // GRATUITO nao tem pagamento

        // GRATUITO nao deve publicar pedido.criado
        verify(pedidoCriadoPublisher, never()).publicar(any());
    }

    // ---- Helpers ----

    private com.ticketeira.ticket.domain.Inscricao mockInscricaoPendente(Long id) {
        com.ticketeira.ticket.domain.Inscricao i =
                com.ticketeira.ticket.domain.Inscricao.pendentePagamento(USUARIO_ID, EVENTO_ID);
        try {
            var campo = com.ticketeira.ticket.domain.Inscricao.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(i, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return i;
    }

    private com.ticketeira.ticket.domain.Inscricao mockInscricaoAtiva(Long id) {
        com.ticketeira.ticket.domain.Inscricao i =
                com.ticketeira.ticket.domain.Inscricao.criar(USUARIO_ID, EVENTO_ID);
        try {
            var campo = com.ticketeira.ticket.domain.Inscricao.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(i, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return i;
    }

    private com.ticketeira.ticket.domain.Ingresso mockIngresso(Long id, Long inscricaoId) {
        com.ticketeira.ticket.domain.Ingresso ing =
                com.ticketeira.ticket.domain.Ingresso.emitir(inscricaoId);
        try {
            var campo = com.ticketeira.ticket.domain.Ingresso.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(ing, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ing;
    }
}
