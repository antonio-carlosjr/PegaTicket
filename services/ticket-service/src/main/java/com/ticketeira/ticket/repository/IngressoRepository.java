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
     * Retorna pares [Inscricao, Ingresso] para evitar N+1.
     *
     * LEFT JOIN (nao INNER): inscricoes PENDENTE_PAGAMENTO ainda NAO tem ingresso e precisam
     * aparecer na tela "Meus ingressos" como "aguardando confirmacao de pagamento"
     * (US-041 criterio 5). Com INNER JOIN essas inscricoes sumiam — o card de pendente nunca
     * renderizava em producao. Filtra a ATIVA (com ingresso) e PENDENTE_PAGAMENTO (sem ingresso);
     * CANCELADA/EXPIRADA ficam de fora da listagem de ingressos.
     */
    @Query("""
            SELECT ins, ing FROM Inscricao ins
            LEFT JOIN Ingresso ing ON ing.inscricaoId = ins.id
            WHERE ins.usuarioId = :userId
              AND ins.status IN (com.ticketeira.ticket.domain.StatusInscricao.ATIVA,
                                 com.ticketeira.ticket.domain.StatusInscricao.PENDENTE_PAGAMENTO)
            ORDER BY ins.inscritoEm DESC
            """)
    List<Object[]> findIngressoComInscricaoByUsuarioId(@Param("userId") Long userId);
}
