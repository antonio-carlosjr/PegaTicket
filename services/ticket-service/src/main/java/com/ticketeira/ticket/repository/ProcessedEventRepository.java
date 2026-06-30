package com.ticketeira.ticket.repository;

import com.ticketeira.ticket.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repositorio de idempotencia dos consumidores AMQP (US-060).
 * Consulta por PK UUID — O(1) pelo indice primario.
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {}
