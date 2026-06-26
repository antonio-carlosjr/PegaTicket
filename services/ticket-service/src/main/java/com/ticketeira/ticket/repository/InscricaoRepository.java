package com.ticketeira.ticket.repository;

import com.ticketeira.ticket.domain.Inscricao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InscricaoRepository extends JpaRepository<Inscricao, Long> {

    boolean existsByUsuarioIdAndEventoId(Long usuarioId, Long eventoId);

    Page<Inscricao> findByUsuarioIdOrderByInscritoEmDesc(Long usuarioId, Pageable pageable);
}
