package com.ticketeira.payment.repository;

import com.ticketeira.payment.domain.Pagamento;
import com.ticketeira.payment.domain.StatusPagamento;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

    Optional<Pagamento> findByInscricaoId(Long inscricaoId);

    /**
     * Lock pessimista para serializar dupla confirmacao concorrente do mesmo inscricaoId.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pagamento p WHERE p.inscricaoId = :id")
    Optional<Pagamento> findByInscricaoIdForUpdate(@Param("id") Long id);

    List<Pagamento> findByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);

    Page<Pagamento> findAll(Pageable pageable);

    Page<Pagamento> findByStatus(StatusPagamento status, Pageable pageable);
}
