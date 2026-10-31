package com.hackathon.podcast.excerpt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recipient_id", nullable = false, length = 64)
    private String recipientId;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private NotificationEventType eventType;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "related_id")
    private UUID relatedId;

    @Column(name = "source_event_id", unique = true)
    private UUID sourceEventId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static Notification create(String recipientId, String message,
                                      NotificationEventType eventType, UUID relatedId) {
        Notification notification = new Notification();
        notification.recipientId = recipientId;
        notification.message = message;
        notification.eventType = eventType;
        notification.relatedId = relatedId;
        notification.read = false;
        notification.createdAt = Instant.now();
        return notification;
    }

    public static Notification fromEvent(String recipientId, String message,
                                         NotificationEventType eventType, UUID relatedId,
                                         UUID sourceEventId, Instant createdAt) {
        Notification notification = create(recipientId, message, eventType, relatedId);
        notification.sourceEventId = sourceEventId;
        notification.createdAt = createdAt;
        return notification;
    }

    public void markRead() {
        this.read = true;
    }
}
