package com.ticketeira.payment.repository;

import com.ticketeira.payment.domain.Pagamento;
import com.ticketeira.payment.domain.StatusPagamento;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
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

    /**
     * Repasse em massa (1 UPDATE condicional — O(1) round-trips, row locks pelo Postgres).
     * Transicao condicional: so altera CONFIRMADO. Retorna qtde de linhas afetadas.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Pagamento p SET p.status = com.ticketeira.payment.domain.StatusPagamento.REPASSADO, "
         + "p.repassadoEm = :agora "
         + "WHERE p.eventoId = :eventoId AND p.status = com.ticketeira.payment.domain.StatusPagamento.CONFIRMADO")
    int repassarConfirmadosDoEvento(@Param("eventoId") Long eventoId, @Param("agora") OffsetDateTime agora);

    /**
     * Carrega CONFIRMADO do evento com lock pessimista para inserir 1 reembolso por linha.
     * Lock serializa corrida repasse-vs-reembolso no mesmo pagamento.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pagamento p WHERE p.eventoId = :eventoId "
         + "AND p.status = com.ticketeira.payment.domain.StatusPagamento.CONFIRMADO")
    List<Pagamento> findConfirmadosDoEventoForUpdate(@Param("eventoId") Long eventoId);
}
