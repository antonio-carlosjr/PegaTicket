package com.ticketeira.ticket.repository;

import com.ticketeira.ticket.domain.Inscricao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface InscricaoRepository extends JpaRepository<Inscricao, Long> {

    boolean existsByUsuarioIdAndEventoId(Long usuarioId, Long eventoId);

    Page<Inscricao> findByUsuarioIdOrderByInscritoEmDesc(Long usuarioId, Pageable pageable);

    /**
     * Retorna inscricoes PENDENTE_PAGAMENTO cuja data de inscricao e anterior ao corte (TTL).
     * Usa o indice parcial idx_inscricoes_pendentes (Sprint 4, V2).
     */
    @Query("SELECT i FROM Inscricao i WHERE i.status = 'PENDENTE_PAGAMENTO' AND i.inscritoEm < :corte")
    List<Inscricao> findPendentesExpiradas(@Param("corte") OffsetDateTime corte);
}
