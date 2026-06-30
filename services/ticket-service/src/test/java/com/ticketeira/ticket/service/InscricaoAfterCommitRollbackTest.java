package com.ticketeira.ticket.service;

import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.client.EventResumo;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A5 — afterCommit nao publica em rollback.
 * Mocka PedidoCriadoPublisher (que usa RabbitTemplate internamente).
 * Forca excecao na tx local de inscrever() apos registerSynchronization.
 * Verifica que o publisher NUNCA e chamado.
 * Caminho feliz verifica que o publisher e chamado 1x.
 * Caso de teste: A5 (tests-spec.md)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("A5 — afterCommit nao publica em rollback (InscricaoService PAGO)")
class InscricaoAfterCommitRollbackTest {

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

        when(eventClient.getEvento(EVENTO_ID))
                .thenReturn(new EventResumo(EVENTO_ID, "Show", "PAGO", "PUBLICADO", 10, 100,
                        new BigDecimal("100.00"), 5L));
        when(inscricaoRepository.existsByUsuarioIdAndEventoId(anyLong(), anyLong())).thenReturn(false);
    }

    @Test
    @DisplayName("A5 — afterCommit_naoPublicaEmRollback: rollback -> publisher nunca chamado (CRITICO)")
    void afterCommit_naoPublicaEmRollback() {
        // Forca excecao na tx local (simula rollback antes do commit)
        when(inscricaoRepository.save(any()))
                .thenThrow(new RuntimeException("Falha simulada — rollback da tx local"));

        try {
            inscricaoService.inscrever(EVENTO_ID, USUARIO_ID);
        } catch (Exception e) {
            // esperado: tx fez rollback
        }

        // CRITICO: com rollback, o afterCommit NAO deve ser executado
        // PedidoCriadoPublisher NUNCA deve ser chamado
        verifyNoInteractions(pedidoCriadoPublisher);
    }

    @Test
    @DisplayName("A5 — caminhoFeliz: commit -> publisher chamado exatamente 1x")
    void afterCommit_caminhoFeliz_publicaUmaVez() {
        com.ticketeira.ticket.domain.Inscricao inscricaoSalva =
                mockInscricao(1L, com.ticketeira.ticket.domain.StatusInscricao.PENDENTE_PAGAMENTO);
        when(inscricaoRepository.save(any())).thenReturn(inscricaoSalva);

        inscricaoService.inscrever(EVENTO_ID, USUARIO_ID);

        // Com o TransactionManager mockado, afterCommit e chamado inline (sem TX real)
        org.mockito.Mockito.verify(pedidoCriadoPublisher, org.mockito.Mockito.times(1))
                .publicar(any(com.ticketeira.ticket.messaging.PedidoCriadoEvent.class));
    }

    // Helper
    private com.ticketeira.ticket.domain.Inscricao mockInscricao(
            Long id, com.ticketeira.ticket.domain.StatusInscricao status) {
        com.ticketeira.ticket.domain.Inscricao i;
        if (status == com.ticketeira.ticket.domain.StatusInscricao.PENDENTE_PAGAMENTO) {
            i = com.ticketeira.ticket.domain.Inscricao.pendentePagamento(USUARIO_ID, EVENTO_ID);
        } else {
            i = com.ticketeira.ticket.domain.Inscricao.criar(USUARIO_ID, EVENTO_ID);
        }
        try {
            var campo = com.ticketeira.ticket.domain.Inscricao.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(i, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return i;
    }
}
