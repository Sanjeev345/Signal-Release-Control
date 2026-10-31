package com.hackathon.podcast.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Versioned Kafka contract published when a producer records a distribution task as done.
 * Notification-only: no downstream workflow depends on it.
 */
public record DistributionTaskCompletedEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        UUID taskId,
        UUID excerptId,
        UUID episodeId,
        String guestId,
        String producerId,
        String taskType,
        String provider,
        Instant completedAt
) {
    public static final String EVENT_TYPE = "DistributionTaskCompleted";
    public static final int EVENT_VERSION = 1;
}
