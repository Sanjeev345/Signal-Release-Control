package com.hackathon.podcast.distribution.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.podcast.contracts.DistributionTaskCompletedEvent;
import com.hackathon.podcast.contracts.EventHeaders;
import com.hackathon.podcast.distribution.domain.DistributedEdit;
import com.hackathon.podcast.distribution.domain.DistributionTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Best-effort, notification-only event. A publish failure must never fail a task update,
 * because the producer's completion record is the source of truth.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskNotificationPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.notification-topic:soa.s14.t04.notifications}")
    private String topic;

    public void publishTaskCompleted(DistributionTask task, DistributedEdit edit,
                                     UUID episodeId, String guestId, String producerId) {
        try {
            UUID eventId = UUID.randomUUID();
            String correlationId = MDC.get("correlationId");
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }
            DistributionTaskCompletedEvent event = new DistributionTaskCompletedEvent(
                    eventId,
                    DistributionTaskCompletedEvent.EVENT_TYPE,
                    DistributionTaskCompletedEvent.EVENT_VERSION,
                    Instant.now(),
                    correlationId,
                    task.getId(),
                    task.getExcerptId(),
                    episodeId,
                    guestId,
                    producerId,
                    task.getTaskType().name(),
                    edit.getProvider(),
                    task.getCompletedAt());
            String payload = objectMapper.writeValueAsString(event);

            ProducerRecord<String, String> record =
                    new ProducerRecord<>(topic, task.getExcerptId().toString(), payload);
            record.headers()
                    .add(EventHeaders.EVENT_TYPE,
                            DistributionTaskCompletedEvent.EVENT_TYPE.getBytes(StandardCharsets.UTF_8))
                    .add(EventHeaders.EVENT_VERSION,
                            String.valueOf(DistributionTaskCompletedEvent.EVENT_VERSION)
                                    .getBytes(StandardCharsets.UTF_8))
                    .add(EventHeaders.CORRELATION_ID, correlationId.getBytes(StandardCharsets.UTF_8))
                    .add(EventHeaders.PRODUCER,
                            "distribution-task-service".getBytes(StandardCharsets.UTF_8));

            kafkaTemplate.send(record).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.warn("Task completion notification failed taskId={} error={}",
                            task.getId(), ex.getMessage());
                }
            });
        } catch (Exception ex) {
            log.warn("Task completion notification skipped taskId={} error={}",
                    task.getId(), ex.getMessage());
        }
    }
}
