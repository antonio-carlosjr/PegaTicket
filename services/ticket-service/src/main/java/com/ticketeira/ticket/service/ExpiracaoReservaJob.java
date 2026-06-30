package com.ticketeira.ticket.service;

import com.ticketeira.ticket.client.EventClient;
import com.ticketeira.ticket.domain.Inscricao;
import com.ticketeira.ticket.repository.InscricaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
     * Executado periodicamente (fixedDelay configuravel via app.reserva.ttl-min).
     * Busca pendentes vencidas e as expira, liberando as vagas correspondentes.
     */
    @Scheduled(fixedDelayString = "${app.reserva.job-delay-ms:300000}")
    @Transactional
    public void executar() {
        OffsetDateTime corte = OffsetDateTime.now().minusMinutes(ttlMinutos);
        List<Inscricao> vencidas = inscricaoRepository.findPendentesExpiradas(corte);

        if (vencidas.isEmpty()) {
            return;
        }

        log.info("ExpiracaoReservaJob: {} inscricao(oes) vencida(s) para expirar (TTL={}min)",
                vencidas.size(), ttlMinutos);

        for (Inscricao inscricao : vencidas) {
            try {
                inscricao.expirar();
                inscricaoRepository.save(inscricao);
                liberarVagaComLog(inscricao);
            } catch (Exception e) {
                // Inscricao ja em outro estado (ex.: ativada entre a query e o expirar) — seguro ignorar
                log.warn("Nao foi possivel expirar inscricaoId={}: {}", inscricao.getId(), e.getMessage());
            }
        }
    }

    private void liberarVagaComLog(Inscricao inscricao) {
        try {
            eventClient.liberarVaga(inscricao.getEventoId());
            log.info("Vaga liberada por TTL: inscricaoId={} eventoId={}",
                    inscricao.getId(), inscricao.getEventoId());
        } catch (Exception e) {
            log.error("[RECONCILIACAO] Falha ao liberar vaga por TTL: inscricaoId={} eventoId={}: {}",
                    inscricao.getId(), inscricao.getEventoId(), e.getMessage());
        }
    }
}
