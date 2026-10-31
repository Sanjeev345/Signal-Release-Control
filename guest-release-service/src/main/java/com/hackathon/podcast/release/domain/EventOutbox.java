package com.hackathon.podcast.release.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_outbox",
        uniqueConstraints = @UniqueConstraint(name = "uq_outbox_aggregate_event",
                columnNames = {"aggregate_id", "event_type"}))
@Getter
@Setter
@NoArgsConstructor
public class EventOutbox {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    public static EventOutbox create(UUID eventId, String aggregateType, UUID aggregateId,
                                     String eventType, String payloadJson, Instant createdAt) {
        EventOutbox outbox = new EventOutbox();
        outbox.id = eventId;
        outbox.aggregateType = aggregateType;
        outbox.aggregateId = aggregateId;
        outbox.eventType = eventType;
        outbox.payloadJson = payloadJson;
        outbox.createdAt = createdAt;
        outbox.attemptCount = 0;
        return outbox;
    }
}
