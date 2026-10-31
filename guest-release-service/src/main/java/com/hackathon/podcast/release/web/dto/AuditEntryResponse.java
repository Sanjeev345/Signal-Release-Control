package com.hackathon.podcast.release.web.dto;

import com.hackathon.podcast.release.domain.ReleaseAudit;

import java.time.Instant;
import java.util.UUID;

public record AuditEntryResponse(
        UUID id,
        UUID releaseId,
        UUID scopeId,
        UUID excerptId,
        String action,
        String actorId,
        String reason,
        Instant occurredAt,
        UUID eventId,
        String correlationId
) {
    public static AuditEntryResponse from(ReleaseAudit audit) {
        return new AuditEntryResponse(
                audit.getId(),
                audit.getReleaseId(),
                audit.getScopeId(),
                audit.getExcerptId(),
                audit.getAction(),
                audit.getActorId(),
                audit.getReason(),
                audit.getOccurredAt(),
                audit.getEventId(),
                audit.getCorrelationId());
    }
}
