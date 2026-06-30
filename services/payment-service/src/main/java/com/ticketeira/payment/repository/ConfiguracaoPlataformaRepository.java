package com.ticketeira.payment.repository;

import com.ticketeira.payment.domain.ConfiguracaoPlataforma;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConfiguracaoPlataformaRepository extends JpaRepository<ConfiguracaoPlataforma, Long> {

    Optional<ConfiguracaoPlataforma> findFirstByOrderByVigenteDesdeDesc();
}
