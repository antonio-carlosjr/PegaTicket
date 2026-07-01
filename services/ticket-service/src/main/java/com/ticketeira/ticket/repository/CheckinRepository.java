package com.ticketeira.ticket.repository;

import com.ticketeira.ticket.domain.Checkin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckinRepository extends JpaRepository<Checkin, Long> {

    /** Pre-check opcional; a barreira definitiva e o UNIQUE(ingresso_id) no banco. */
    boolean existsByIngressoId(Long ingressoId);
}
