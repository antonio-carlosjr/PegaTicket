package com.ticketeira.event.repository;

import com.ticketeira.event.domain.Evento;
import com.ticketeira.event.domain.StatusEvento;
import com.ticketeira.event.domain.TipoEvento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface EventRepository extends JpaRepository<Evento, Long> {

    Page<Evento> findByPromotorId(Long promotorId, Pageable pageable);

    /**
     * Decremento atomico anti-overbooking (ADR-T07).
     * Um unico UPDATE condicional: a clausula WHERE e a checagem.
     * Retorna 1 se decrementou, 0 se esgotado/nao-publicado/inexistente.
     * O row lock do Postgres serializa concorrentes na mesma linha.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Evento e
               SET e.vagasDisponiveis = e.vagasDisponiveis - 1
             WHERE e.id = :id
               AND e.status = com.ticketeira.event.domain.StatusEvento.PUBLICADO
               AND e.vagasDisponiveis > 0
            """)
    int decrementarVaga(@Param("id") Long id);

    /**
     * Incremento de compensacao limitado pela capacidade (ADR-T07).
     * Retorna 1 se incrementou, 0 se ja no teto ou nao-publicado (no-op idempotente).
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Evento e
               SET e.vagasDisponiveis = e.vagasDisponiveis + 1
             WHERE e.id = :id
               AND e.status = com.ticketeira.event.domain.StatusEvento.PUBLICADO
               AND e.vagasDisponiveis < e.capacidade
            """)
    int incrementarVaga(@Param("id") Long id);

    /**
     * Busca eventos PUBLICADOS com filtros opcionais.
     * CAST(:q AS string) e CAST(:de AS timestamp) sao obrigatorios para evitar
     * o bug do Postgres "lower(bytea) does not exist" quando o parametro e null
     * (reproducao exata do bug da Sprint 1 no UsuarioRepository).
     */
    @Query("""
            SELECT e FROM Evento e
            WHERE e.status = com.ticketeira.event.domain.StatusEvento.PUBLICADO
              AND (CAST(:q AS string) IS NULL
                   OR LOWER(e.titulo) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                   OR LOWER(e.local)  LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
              AND (:tipo IS NULL OR e.tipo = :tipo)
              AND (CAST(:de AS timestamp) IS NULL OR e.dataInicio >= :de)
              AND (CAST(:ate AS timestamp) IS NULL OR e.dataInicio <= :ate)
            """)
    Page<Evento> buscarPublicados(@Param("q") String q,
                                  @Param("tipo") TipoEvento tipo,
                                  @Param("de") OffsetDateTime de,
                                  @Param("ate") OffsetDateTime ate,
                                  Pageable pageable);
}
