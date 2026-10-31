package com.hackathon.podcast.excerpt.service;

import com.hackathon.podcast.excerpt.domain.Notification;
import com.hackathon.podcast.excerpt.domain.NotificationEventType;
import com.hackathon.podcast.excerpt.repository.NotificationRepository;
import com.hackathon.podcast.excerpt.web.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final NotificationRepository notificationRepository;

    @Transactional
    public void notify(String recipientId, String message, NotificationEventType eventType, UUID relatedId) {
        if (recipientId == null || recipientId.isBlank()) {
            return;
        }
        notificationRepository.save(Notification.create(recipientId, truncate(message), eventType, relatedId));
    }

    /**
     * Records a notification derived from an external event exactly once.
     * The unique sourceEventId column protects against duplicate delivery.
     */
    @Transactional
    public void recordExternal(String recipientId, String message, NotificationEventType eventType,
                               UUID relatedId, UUID sourceEventId) {
        if (recipientId == null || recipientId.isBlank()) {
            return;
        }
        if (sourceEventId != null && notificationRepository.existsBySourceEventId(sourceEventId)) {
            return;
        }
        notificationRepository.save(Notification.fromEvent(
                recipientId, truncate(message), eventType, relatedId, sourceEventId, Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(String recipientId, boolean unreadOnly) {
        List<Notification> notifications = unreadOnly
                ? notificationRepository.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(recipientId)
                : notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
        return notifications.stream().map(NotificationResponse::from).toList();
    }

    @Transactional
    public NotificationResponse markRead(UUID notificationId, String recipientId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification " + notificationId + " was not found"));
        notification.markRead();
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional
    public int markAllRead(String recipientId) {
        return notificationRepository.markAllRead(recipientId);
    }

    private static String truncate(String message) {
        String value = message == null ? "" : message.trim();
        return value.length() <= MAX_MESSAGE_LENGTH ? value : value.substring(0, MAX_MESSAGE_LENGTH);
    }
}
