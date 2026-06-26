package com.ticketeira.event.repository;

import com.ticketeira.event.domain.Evento;
import com.ticketeira.event.domain.StatusEvento;
import com.ticketeira.event.domain.TipoEvento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface EventRepository extends JpaRepository<Evento, Long> {

    Page<Evento> findByPromotorId(Long promotorId, Pageable pageable);

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
