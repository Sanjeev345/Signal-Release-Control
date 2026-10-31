package com.hackathon.podcast.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Versioned Kafka contract for a single excerpt withdrawal.
 * This module intentionally contains no entities, repositories or business logic.
 */
public record GuestExcerptWithdrawnEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        UUID releaseId,
        UUID releaseScopeId,
        UUID episodeId,
        UUID excerptId,
        String guestId,
        String withdrawnBy,
        String reason
) {
    public static final String EVENT_TYPE = "GuestExcerptWithdrawn";
    public static final int EVENT_VERSION = 1;
}
