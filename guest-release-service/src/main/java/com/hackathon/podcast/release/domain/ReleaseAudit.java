package com.hackathon.podcast.release.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "release_audit",
        uniqueConstraints = @UniqueConstraint(name = "uq_release_audit_event", columnNames = "event_id"))
@Getter
@Setter
@NoArgsConstructor
public class ReleaseAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "release_id", nullable = false)
    private UUID releaseId;

    @Column(name = "scope_id", nullable = false)
    private UUID scopeId;

    @Column(name = "excerpt_id", nullable = false)
    private UUID excerptId;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(name = "actor_id", nullable = false, length = 64)
    private String actorId;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;

    public static ReleaseAudit withdrawal(UUID releaseId, UUID scopeId, UUID excerptId,
                                          String actorId, String reason, Instant occurredAt,
                                          UUID eventId, String correlationId) {
        ReleaseAudit audit = new ReleaseAudit();
        audit.releaseId = releaseId;
        audit.scopeId = scopeId;
        audit.excerptId = excerptId;
        audit.action = "WITHDRAWAL";
        audit.actorId = actorId;
        audit.reason = reason;
        audit.occurredAt = occurredAt;
        audit.eventId = eventId;
        audit.correlationId = correlationId;
        return audit;
    }
}
