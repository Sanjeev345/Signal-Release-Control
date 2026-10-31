package com.hackathon.podcast.excerpt.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.podcast.contracts.DistributionTaskCompletedEvent;
import com.hackathon.podcast.excerpt.domain.NotificationEventType;
import com.hackathon.podcast.excerpt.repository.PodcastExcerptRepository;
import com.hackathon.podcast.excerpt.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Optional guest-facing notification when a producer records a distribution task as done.
 * This is a notification only; it does not imply anything was removed from an external platform.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskCompletedNotificationListener {

    private final ObjectMapper objectMapper;
    private final PodcastExcerptRepository excerptRepository;
    private final NotificationService notificationService;

    @KafkaListener(
            id = "excerpt-task-completed-notification",
            topics = "${app.kafka.notification-topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onTaskCompleted(ConsumerRecord<String, String> record) {
        DistributionTaskCompletedEvent event;
        try {
            event = objectMapper.readValue(record.value(), DistributionTaskCompletedEvent.class);
        } catch (Exception ex) {
            log.warn("Ignoring unreadable task notification key={} offset={} error={}",
                    record.key(), record.offset(), ex.getMessage());
            return;
        }
        if (!DistributionTaskCompletedEvent.EVENT_TYPE.equals(event.eventType())
                || event.eventId() == null || event.guestId() == null || event.guestId().isBlank()) {
            return;
        }

        String message = "The " + event.taskType() + " task on " + event.provider()
                + " for your excerpt is marked done by the producer. This records completion only;"
                + " it does not confirm removal from the external platform.";
        notificationService.recordExternal(
                event.guestId(),
                message,
                NotificationEventType.TASK_COMPLETED,
                event.taskId() == null ? event.excerptId() : event.taskId(),
                event.eventId());
        log.info("Recorded task completion notification guestId={} taskId={} eventId={}",
                event.guestId(), event.taskId(), event.eventId());
    }
}
