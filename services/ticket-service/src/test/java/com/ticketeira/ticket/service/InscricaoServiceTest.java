package com.ticketeira.ticket.service;

import com.ticketeira.common.exception.BusinessException;
import com.ticketeira.common.exception.NotFoundException;
import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.client.EventResumo;
import com.ticketeira.ticket.domain.Ingresso;
import com.ticketeira.ticket.domain.Inscricao;
import com.ticketeira.ticket.dto.InscricaoResponse;
import com.ticketeira.ticket.messaging.PedidoCriadoPublisher;
import com.ticketeira.ticket.repository.IngressoRepository;
import com.ticketeira.ticket.repository.InscricaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitarios do InscricaoService com Mockito.
 * EventClient e repositorios sao mockados.
 * TransactionTemplate configurada com PlatformTransactionManager em modo "sem transacao real"
 * (mock que executa o callback imediatamente, sem TX de banco).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InscricaoServiceTest {

    @Mock
    InscricaoRepository inscricaoRepository;

    @Mock
    IngressoRepository ingressoRepository;

    @Mock
    EventClient eventClient;

    @Mock
    PedidoCriadoPublisher pedidoCriadoPublisher;

    // Criado manualmente (nao @InjectMocks) para passar PlatformTransactionManager mockado
    InscricaoService inscricaoService;

    private static final Long USUARIO_ID = 10L;
    private static final Long EVENTO_ID = 42L;

    @BeforeEach
    void setUp() {
        // TransactionManager mock que executa o callback sem abrir TX real
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        TransactionStatus txStatus = mock(TransactionStatus.class);
        when(txManager.getTransaction(any())).thenReturn(txStatus);

        inscricaoService = new InscricaoService(
                inscricaoRepository, ingressoRepository, eventClient,
                pedidoCriadoPublisher, txManager);

        // Defaults: evento GRATUITO PUBLICADO, usuario nao inscrito
        when(eventClient.getEvento(EVENTO_ID))
                .thenReturn(new EventResumo(EVENTO_ID, "Show Gratuito", "GRATUITO", "PUBLICADO", 10, 100,
                        null, 1L, java.time.OffsetDateTime.now().plusDays(30), null));
        when(inscricaoRepository.existsByUsuarioIdAndEventoId(USUARIO_ID, EVENTO_ID)).thenReturn(false);
    }

    // ---- B1: Caminho-feliz ----

    @Test
    void inscrever_gratuitoPublicado_cria201ComIngresso() {
        Inscricao inscricaoSalva = inscricaoComId(1L);
        Ingresso ingressoSalvo = ingressoComId(1L, 1L);
        when(inscricaoRepository.save(any())).thenReturn(inscricaoSalva);
        when(ingressoRepository.save(any())).thenReturn(ingressoSalvo);

        InscricaoResponse resp = inscricaoService.inscrever(EVENTO_ID, USUARIO_ID);

        assertThat(resp).isNotNull();
        assertThat(resp.eventoId()).isEqualTo(EVENTO_ID);
        assertThat(resp.status()).isEqualTo("ATIVA");
        assertThat(resp.ingresso()).isNotNull();
        assertThat(resp.ingresso().codigoUnico()).isNotBlank();
    }

    @Test
    void inscrever_ordemDaSagaRespeitada() {
        when(inscricaoRepository.save(any())).thenReturn(inscricaoComId(1L));
        when(ingressoRepository.save(any())).thenReturn(ingressoComId(1L, 1L));

        inscricaoService.inscrever(EVENTO_ID, USUARIO_ID);

        InOrder ordem = inOrder(eventClient, inscricaoRepository, ingressoRepository);
        ordem.verify(eventClient).getEvento(EVENTO_ID);
        ordem.verify(inscricaoRepository).existsByUsuarioIdAndEventoId(USUARIO_ID, EVENTO_ID);
        ordem.verify(eventClient).reservarVaga(EVENTO_ID);
        ordem.verify(inscricaoRepository).save(any());
        ordem.verify(ingressoRepository).save(any());
    }

    // ---- B2: Bloqueios de estado/tipo ----

    @Test
    void inscrever_eventoPago_criaPendentePagamento_semIngresso() {
        // Sprint 4: evento PAGO agora e suportado (ramo PAGO da saga)
        when(eventClient.getEvento(EVENTO_ID))
                .thenReturn(new EventResumo(EVENTO_ID, "Festival", "PAGO", "PUBLICADO", 10, 100,
                        new java.math.BigDecimal("99.00"), 1L,
                        java.time.OffsetDateTime.now().plusDays(30), 7));

        com.ticketeira.ticket.domain.Inscricao inscricaoPaga =
                com.ticketeira.ticket.domain.Inscricao.pendentePagamento(USUARIO_ID, EVENTO_ID);
        try {
            var campo = com.ticketeira.ticket.domain.Inscricao.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(inscricaoPaga, 1L);
        } catch (Exception e) { throw new RuntimeException(e); }
        when(inscricaoRepository.save(any())).thenReturn(inscricaoPaga);

        InscricaoResponse resp = inscricaoService.inscrever(EVENTO_ID, USUARIO_ID);

        assertThat(resp.status()).isEqualTo("PENDENTE_PAGAMENTO");
        assertThat(resp.ingresso()).isNull();
        assertThat(resp.pagamento()).isNotNull();
        verify(ingressoRepository, never()).save(any());
    }

    @Test
    void inscrever_eventoNaoPublicado_lanca422_semReservar() {
        when(eventClient.getEvento(EVENTO_ID))
                .thenReturn(new EventResumo(EVENTO_ID, "Rascunho", "GRATUITO", "RASCUNHO", null, 100,
                        null, 1L, java.time.OffsetDateTime.now().plusDays(30), null));

        assertThatThrownBy(() -> inscricaoService.inscrever(EVENTO_ID, USUARIO_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("EVENTO_NAO_PUBLICADO");

        verify(eventClient, never()).reservarVaga(any());
    }

    @Test
    void inscrever_eventoInexistente_lanca404_semReservar() {
        when(eventClient.getEvento(EVENTO_ID)).thenThrow(new NotFoundException("EVENTO_NAO_ENCONTRADO"));

        assertThatThrownBy(() -> inscricaoService.inscrever(EVENTO_ID, USUARIO_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("EVENTO_NAO_ENCONTRADO");

        verify(eventClient, never()).reservarVaga(any());
    }

    @Test
    void inscrever_eventServiceDown_lanca503_semReservar() {
        when(eventClient.getEvento(EVENTO_ID)).thenThrow(new BusinessException("EVENTO_INDISPONIVEL", 503));

        assertThatThrownBy(() -> inscricaoService.inscrever(EVENTO_ID, USUARIO_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(503);

        verify(eventClient, never()).reservarVaga(any());
    }

    @Test
    void inscrever_esgotado_lanca409_semInscrever() {
        doThrow(new BusinessException("EVENTO_ESGOTADO", 409))
                .when(eventClient).reservarVaga(EVENTO_ID);

        assertThatThrownBy(() -> inscricaoService.inscrever(EVENTO_ID, USUARIO_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("EVENTO_ESGOTADO");

        verify(inscricaoRepository, never()).save(any());
    }

    // ---- B3: Dupla inscricao (pre-check) ----

    @Test
    void inscrever_jaInscrito_preCheck_lanca409_semReservar() {
        when(inscricaoRepository.existsByUsuarioIdAndEventoId(USUARIO_ID, EVENTO_ID)).thenReturn(true);

        assertThatThrownBy(() -> inscricaoService.inscrever(EVENTO_ID, USUARIO_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("JA_INSCRITO");

        verify(eventClient, never()).reservarVaga(any());
    }

    // ---- B5: Compensacao — UNIQUE viola apos reserva ----

    @Test
    void inscrever_uniqueVioladoNaTxLocal_compensa_lanca409() {
        when(inscricaoRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("uk_inscricao_usuario_evento"));

        assertThatThrownBy(() -> inscricaoService.inscrever(EVENTO_ID, USUARIO_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("JA_INSCRITO");

        verify(eventClient).liberarVaga(EVENTO_ID);
    }

    @Test
    void inscrever_falhaGenerica_compensa_lanca503() {
        when(inscricaoRepository.save(any()))
                .thenThrow(new RuntimeException("DB fora"));

        assertThatThrownBy(() -> inscricaoService.inscrever(EVENTO_ID, USUARIO_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(503);

        verify(eventClient).liberarVaga(EVENTO_ID);
    }

    @Test
    void inscrever_compensacaoTambemFalha_naoPropagarCompensacao_lancaExcecaoOriginal() {
        when(inscricaoRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("unique"));
        doThrow(new BusinessException("COMPENSACAO_FALHOU", 503))
                .when(eventClient).liberarVaga(EVENTO_ID);

        // Deve lancar JA_INSCRITO (excecao original), nao a de compensacao
        assertThatThrownBy(() -> inscricaoService.inscrever(EVENTO_ID, USUARIO_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("JA_INSCRITO");
    }

    // ---- Helpers ----

    private Inscricao inscricaoComId(Long id) {
        Inscricao i = Inscricao.criar(USUARIO_ID, EVENTO_ID);
        try {
            var campo = Inscricao.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(i, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return i;
    }

    private Ingresso ingressoComId(Long id, Long inscricaoId) {
        Ingresso ing = Ingresso.emitir(inscricaoId);
        try {
            var campo = Ingresso.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(ing, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ing;
    }
}
