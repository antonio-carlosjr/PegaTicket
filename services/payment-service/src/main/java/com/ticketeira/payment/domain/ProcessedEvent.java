package com.ticketeira.payment.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro de evento processado — chave de idempotencia para consumidores RabbitMQ.
 * A PK (eventId) e o UUID da origem; INSERT com PK duplicada sinaliza reentrega.
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
