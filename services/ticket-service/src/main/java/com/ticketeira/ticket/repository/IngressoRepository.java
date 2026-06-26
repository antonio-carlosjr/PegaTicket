package com.ticketeira.ticket.repository;

import com.ticketeira.ticket.domain.Ingresso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IngressoRepository extends JpaRepository<Ingresso, Long> {

    /**
     * Busca ingressos do usuario via join com inscricoes, sem N+1.
     * Retorna todos os ingressos cujo inscricao.usuarioId == userId.
     */
    @Query("""
            SELECT ing FROM Ingresso ing
            JOIN Inscricao ins ON ing.inscricaoId = ins.id
            WHERE ins.usuarioId = :userId
            """)
    List<Ingresso> findByUsuarioId(@Param("userId") Long userId);

    /**
     * Usado internamente para construir MeuIngressoResponse com dados da inscricao.
     * Retorna pares [Ingresso, Inscricao] para evitar N+1.
     */
    @Query("""
            SELECT ing, ins FROM Ingresso ing
            JOIN Inscricao ins ON ing.inscricaoId = ins.id
            WHERE ins.usuarioId = :userId
            """)
    List<Object[]> findIngressoComInscricaoByUsuarioId(@Param("userId") Long userId);
}
