package com.ticketeira.ticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro de idempotencia dos consumidores AMQP (US-060).
 * PK = eventId do payload (gerado na origem — produtor).
 * A colisao de PK na segunda entrega desfaz o efeito na mesma tx (at-least-once -> exactly-once-effect).
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "routing_key", nullable = false, length = 80)
    private String routingKey;

    @Column(name = "processado_em", nullable = false)
    private OffsetDateTime processadoEm;

    protected ProcessedEvent() {}

    public static ProcessedEvent of(UUID eventId, String routingKey) {
        ProcessedEvent pe = new ProcessedEvent();
        pe.eventId = eventId;
        pe.routingKey = routingKey;
        pe.processadoEm = OffsetDateTime.now();
        return pe;
    }

    public UUID getEventId() { return eventId; }
    public String getRoutingKey() { return routingKey; }
    public OffsetDateTime getProcessadoEm() { return processadoEm; }
}
