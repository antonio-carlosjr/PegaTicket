package com.ticketeira.payment.service;

import com.ticketeira.payment.domain.Pagamento;
import com.ticketeira.payment.domain.StatusPagamento;
import com.ticketeira.payment.messaging.PagamentoAprovadoPublisher;
import com.ticketeira.payment.repository.ConfiguracaoPlataformaRepository;
import com.ticketeira.payment.repository.PagamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Testes unitarios de afterCommit nao publica em rollback (B4).
 * Usa Mockito puro — sem Spring, sem RabbitMQ real.
 * Forca uma excecao na tx local de confirmar() ANTES do commit
 * e verifica que RabbitTemplate nunca e chamado.
 * Caso de teste: B4 (tests-spec.md)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("B4 — afterCommit nao publica em rollback")
class AfterCommitRollbackTest {

    @Mock
    PagamentoRepository pagamentoRepository;

    @Mock
    ConfiguracaoPlataformaRepository configuracaoRepository;

    @Mock
    RabbitTemplate rabbitTemplate;

    @Mock
    GatewaySimulado gatewaySimulado;

    PagamentoService pagamentoService;

    private static final Long INSCRICAO_ID = 1L;
    private static final Long USUARIO_ID = 10L;

    @BeforeEach
    void setUp() {
        // TransactionManager mock que executa o callback sem abrir TX real
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        TransactionStatus txStatus = mock(TransactionStatus.class);
        when(txManager.getTransaction(any())).thenReturn(txStatus);

        PagamentoAprovadoPublisher publisher = new PagamentoAprovadoPublisher(rabbitTemplate);
        pagamentoService = new PagamentoService(
                pagamentoRepository, configuracaoRepository, gatewaySimulado, publisher, txManager);
    }

    @Test
    @DisplayName("confirmar_afterCommit_naoPublicaEmRollback — RabbitTemplate nunca chamado se tx faz rollback")
    void confirmar_afterCommit_naoPublicaEmRollback() {
        // Pagamento PENDENTE existente
        Pagamento pagamentoPendente = Pagamento.pendente(
                INSCRICAO_ID, USUARIO_ID, new BigDecimal("100.00"), new BigDecimal("0.1000"));

        when(pagamentoRepository.findByInscricaoIdForUpdate(INSCRICAO_ID))
                .thenReturn(Optional.of(pagamentoPendente));

        // Gateway aprova
        when(gatewaySimulado.aprovar(any(BigDecimal.class))).thenReturn("SIM-xyz");

        // Forca falha no save (simula rollback)
        when(pagamentoRepository.save(any(Pagamento.class)))
                .thenThrow(new RuntimeException("Falha simulada de banco — rollback"));

        // Chama confirmar: deve lancar excecao
        try {
            pagamentoService.confirmar(INSCRICAO_ID, USUARIO_ID);
        } catch (Exception e) {
            // esperado — o rollback acontece aqui
        }

        // CRITICO: RabbitTemplate NUNCA deve ser chamado se houve rollback
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("confirmar_semRollback_publicaUmaVez — caminho feliz deve chamar convertAndSend 1x")
    void confirmar_semRollback_publicaUmaVezAposCommit() {
        // Este teste verifica o lado positivo: apos commit bem-sucedido, publica 1x.
        // Com o mock de tx sincrono (sem tx real), o afterCommit e chamado inline.
        Pagamento pagamentoPendente = Pagamento.pendente(
                INSCRICAO_ID, USUARIO_ID, new BigDecimal("100.00"), new BigDecimal("0.1000"));

        when(pagamentoRepository.findByInscricaoIdForUpdate(INSCRICAO_ID))
                .thenReturn(Optional.of(pagamentoPendente));
        when(gatewaySimulado.aprovar(any(BigDecimal.class))).thenReturn("SIM-xyz");
        when(pagamentoRepository.save(any(Pagamento.class))).thenReturn(pagamentoPendente);

        pagamentoService.confirmar(INSCRICAO_ID, USUARIO_ID);

        // afterCommit e chamado apos commit — verificar que rabbitTemplate foi chamado 1x
        // (a verificacao exata do routing key / exchange fica nos testes de integracao B2.b)
        org.mockito.Mockito.verify(rabbitTemplate, org.mockito.Mockito.times(1))
                .convertAndSend(anyString(), anyString(), any(Object.class));
    }
}
