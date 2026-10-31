package com.hackathon.podcast.excerpt.web.dto;

import com.hackathon.podcast.excerpt.domain.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String message,
        String eventType,
        boolean read,
        UUID relatedId,
        Instant createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getMessage(),
                notification.getEventType().name(),
                notification.isRead(),
                notification.getRelatedId(),
                notification.getCreatedAt());
    }
}
