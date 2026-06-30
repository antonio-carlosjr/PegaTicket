package com.ticketeira.ticket.messaging;

import com.ticketeira.ticket.domain.Ingresso;
import com.ticketeira.ticket.domain.Inscricao;
import com.ticketeira.ticket.domain.ProcessedEvent;
import com.ticketeira.ticket.domain.StatusInscricao;
import com.ticketeira.ticket.repository.IngressoRepository;
import com.ticketeira.ticket.repository.InscricaoRepository;
import com.ticketeira.ticket.repository.ProcessedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regressao CR-S4-01 (P0, Risco R6): pagamento.aprovado entregue para uma inscricao que NAO
 * esta mais PENDENTE_PAGAMENTO (ex.: ja expirada pelo ExpiracaoReservaJob ou ja ativada por
 * uma entrega anterior). O consumidor deve:
 *   - persistir o processed_events (sem desfazer),
 *   - NAO emitir ingresso, NAO chamar ativar() (que lancaria),
 *   - retornar normalmente (ACK / no-op idempotente), evitando loop de re-entrega -> DLQ.
 *
 * Teste rapido (Mockito, sem broker/contexto Spring) — roda na suite unitaria local.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CR-S4-01 — PagamentoAprovadoListener guard de estado (R6)")
class PagamentoAprovadoListenerGuardTest {

    @Mock InscricaoRepository inscricaoRepository;
    @Mock IngressoRepository ingressoRepository;
    @Mock ProcessedEventRepository processedEventRepository;

    @InjectMocks PagamentoAprovadoListener listener;

    private static Inscricao inscricaoComStatus(StatusInscricao status) {
        Inscricao i = Inscricao.pendentePagamento(10L, 100L);
        // PENDENTE_PAGAMENTO e o factory; aplica a transicao desejada quando necessario
        if (status == StatusInscricao.EXPIRADA) {
            i.expirar();
        } else if (status == StatusInscricao.ATIVA) {
            i.ativar();
        }
        return i;
    }

    private static PagamentoAprovadoEvent evento() {
        return new PagamentoAprovadoEvent(
                UUID.randomUUID(), 1L, 5L, 10L, 100L, OffsetDateTime.now());
    }

    @Test
    @DisplayName("inscricao EXPIRADA -> nao emite ingresso, nao lanca, ACK (no-op)")
    void inscricaoExpirada_naoEmiteIngresso_eNaoLanca() {
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(inscricaoRepository.findById(5L))
                .thenReturn(Optional.of(inscricaoComStatus(StatusInscricao.EXPIRADA)));

        // NAO deve lancar
        listener.consumir(evento());

        // Nenhum ingresso emitido, nenhuma ativacao salva
        verify(ingressoRepository, never()).save(any(Ingresso.class));
        verify(inscricaoRepository, never()).save(any(Inscricao.class));
        // O processed_events foi gravado (a entrega foi consumida, ACK)
        verify(processedEventRepository, times(1)).saveAndFlush(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("inscricao ja ATIVA -> nao re-emite ingresso, nao lanca (no-op)")
    void inscricaoJaAtiva_naoReemite_eNaoLanca() {
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(inscricaoRepository.findById(5L))
                .thenReturn(Optional.of(inscricaoComStatus(StatusInscricao.ATIVA)));

        listener.consumir(evento());

        verify(ingressoRepository, never()).save(any(Ingresso.class));
        verify(inscricaoRepository, never()).save(any(Inscricao.class));
    }

    @Test
    @DisplayName("inscricao PENDENTE_PAGAMENTO -> emite ingresso e ativa (caminho feliz)")
    void inscricaoPendente_emiteIngresso_eAtiva() {
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        Inscricao pendente = inscricaoComStatus(StatusInscricao.PENDENTE_PAGAMENTO);
        when(inscricaoRepository.findById(5L)).thenReturn(Optional.of(pendente));
        when(ingressoRepository.save(any(Ingresso.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        listener.consumir(evento());

        verify(inscricaoRepository, times(1)).save(any(Inscricao.class));
        verify(ingressoRepository, times(1)).save(any(Ingresso.class));
    }
}
