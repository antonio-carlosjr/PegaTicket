package com.ticketeira.ticket.service;

import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.domain.Inscricao;
import com.ticketeira.ticket.repository.InscricaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Job agendado que expira inscricoes PENDENTE_PAGAMENTO mais velhas que o TTL (ADR-T10, Risco R1).
 * Libera a vaga no event-service como compensacao, tornando o slot disponivel novamente.
 * Idempotente: so age em pendentes vencidas (usa idx_inscricoes_pendentes na query).
 * Habilitado via @EnableScheduling na aplicacao.
 */
@Component
public class ExpiracaoReservaJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiracaoReservaJob.class);

    private final InscricaoRepository inscricaoRepository;
    private final EventClient eventClient;

    @Value("${app.reserva.ttl-min:30}")
    private int ttlMinutos;

    public ExpiracaoReservaJob(InscricaoRepository inscricaoRepository,
                               EventClient eventClient) {
        this.inscricaoRepository = inscricaoRepository;
        this.eventClient = eventClient;
    }

    /**
     * Executado periodicamente (fixedDelay configuravel via app.reserva.job-delay-ms).
     * Busca pendentes vencidas e as expira, liberando as vagas correspondentes.
     *
     * Nota de design: o metodo NAO e @Transactional. Cada expiracao roda em sua propria tx
     * curta (expirarUmaInscricao) e a chamada HTTP de compensacao (liberarVaga) acontece FORA
     * de qualquer transacao — caso contrario o lock de linha da inscricao ficaria preso durante
     * todas as N chamadas de rede ao event-service (latencia de I/O segurando recurso de banco).
     */
    @Scheduled(fixedDelayString = "${app.reserva.job-delay-ms:300000}")
    public void executar() {
        OffsetDateTime corte = OffsetDateTime.now().minusMinutes(ttlMinutos);
        List<Inscricao> vencidas = inscricaoRepository.findPendentesExpiradas(corte);

        if (vencidas.isEmpty()) {
            return;
        }

        log.info("ExpiracaoReservaJob: {} inscricao(oes) vencida(s) para expirar (TTL={}min)",
                vencidas.size(), ttlMinutos);

        for (Inscricao inscricao : vencidas) {
            // 1) transicao de estado persistida pelo save (tx propria do Spring Data, curta, sem I/O)
            boolean expirou = expirarUmaInscricao(inscricao);
            // 2) compensacao (HTTP) so apos persistir o estado EXPIRADA, fora de qualquer tx
            //    — evita segurar lock/conexao de banco durante a chamada de rede ao event-service.
            if (expirou) {
                liberarVagaComLog(inscricao.getId(), inscricao.getEventoId());
            }
        }
    }

    /**
     * Transiciona PENDENTE_PAGAMENTO -> EXPIRADA e persiste. O save abre sua propria transacao
     * (comportamento padrao do Spring Data) — curta e sem nenhuma I/O de rede dentro dela.
     * Retorna true se a transicao ocorreu (ha vaga a liberar via compensacao).
     */
    private boolean expirarUmaInscricao(Inscricao inscricao) {
        try {
            inscricao.expirar();
            inscricaoRepository.save(inscricao);
            return true;
        } catch (Exception e) {
            // Inscricao ja em outro estado (ex.: ativada entre a query e o expirar) — seguro ignorar
            log.warn("Nao foi possivel expirar inscricaoId={}: {}", inscricao.getId(), e.getMessage());
            return false;
        }
    }

    private void liberarVagaComLog(Long inscricaoId, Long eventoId) {
        try {
            eventClient.liberarVaga(eventoId);
            log.info("Vaga liberada por TTL: inscricaoId={} eventoId={}", inscricaoId, eventoId);
        } catch (Exception e) {
            log.error("[RECONCILIACAO] Falha ao liberar vaga por TTL: inscricaoId={} eventoId={}: {}",
                    inscricaoId, eventoId, e.getMessage());
        }
    }
}
