package com.ticketeira.event.repository;

import com.ticketeira.event.domain.Avaliacao;
import com.ticketeira.event.dto.ReputacaoResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {

    /**
     * Reputacao (US-025): media + total em 1 query agregada — sem N+1.
     * Usa idx_avaliacoes_evento (V1). AVG = null quando nenhuma linha existe.
     */
    @Query("SELECT new com.ticketeira.event.dto.ReputacaoResponse(AVG(a.nota), COUNT(a)) "
         + "FROM Avaliacao a WHERE a.eventoId = :eventoId")
    ReputacaoResponse agregarReputacao(@Param("eventoId") Long eventoId);
}
