package com.ticketeira.ticket.repository;

import com.ticketeira.ticket.domain.Ingresso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IngressoRepository extends JpaRepository<Ingresso, Long> {

    /**
     * Lookup por codigo_unico para check-in (US-034). Usa UNIQUE(codigo_unico) — O(1).
     */
    Optional<Ingresso> findByCodigoUnico(String codigoUnico);

    /**
     * Busca ingresso de uma inscricao (para cancelar no fluxo voluntario, US-035).
     */
    Optional<Ingresso> findByInscricaoId(Long inscricaoId);

    /**
     * Verifica se existe ingresso UTILIZADO para usuario+evento (elegibilidade US-024).
     * JOIN inscricao para obter usuario_id e evento_id.
     * Usa idx_inscricoes_usuario / idx_inscricoes_evento (V1).
     */
    @Query("""
            SELECT COUNT(ing) > 0 FROM Ingresso ing
            JOIN Inscricao ins ON ing.inscricaoId = ins.id
            WHERE ins.usuarioId = :usuarioId AND ins.eventoId = :eventoId
              AND ing.status = com.ticketeira.ticket.domain.StatusIngresso.UTILIZADO
            """)
    boolean existsIngressoUtilizadoByUsuarioIdAndEventoId(
            @Param("usuarioId") Long usuarioId,
            @Param("eventoId") Long eventoId);

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

    /**
     * Cancela em massa os ingressos ATIVO das inscricoes de um evento (US-042).
     * Preserva UTILIZADO (historico de check-in). UPDATE condicional via subquery.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Ingresso ing SET ing.status = com.ticketeira.ticket.domain.StatusIngresso.CANCELADO "
         + "WHERE ing.status = com.ticketeira.ticket.domain.StatusIngresso.ATIVO "
         + "AND ing.inscricaoId IN (SELECT i.id FROM Inscricao i WHERE i.eventoId = :eventoId)")
    int cancelarIngressosDoEvento(@Param("eventoId") Long eventoId);
}
