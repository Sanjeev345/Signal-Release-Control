package com.hackathon.podcast.distribution.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.podcast.contracts.GuestExcerptWithdrawnEvent;
import com.hackathon.podcast.distribution.service.DistributionTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.retry.annotation.Backoff;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class GuestExcerptWithdrawnConsumer {

    private final DistributionTaskService distributionTaskService;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            retryTopicSuffix = "-retry",
            dltTopicSuffix = ".DLT",
            autoCreateTopics = "true")
    @KafkaListener(
            id = "withdrawal-consumer",
            topics = "${app.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onWithdrawal(ConsumerRecord<String, String> record,
                             @Header(name = "correlationId", required = false) byte[] correlationHeader) {
        GuestExcerptWithdrawnEvent event = parseAndValidate(record.value());
        String headerValue = correlationHeader == null
                ? null
                : new String(correlationHeader, StandardCharsets.UTF_8);
        String correlationId = headerValue == null || headerValue.isBlank()
                ? event.correlationId()
                : headerValue;

        int created = distributionTaskService.processWithdrawal(event, correlationId);
        log.info("Consumed GuestExcerptWithdrawn key={} eventId={} excerptId={} createdTasks={} correlationId={}",
                record.key(), event.eventId(), event.excerptId(), created, correlationId);
    }

    @KafkaListener(
            id = "withdrawal-dlt",
            topics = "${app.kafka.topic}.DLT",
            groupId = "${spring.kafka.consumer.group-id}-dlt")
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        log.error("Dead-lettered withdrawal event key={} partition={} offset={} payload={}",
                record.key(), record.partition(), record.offset(), record.value());
    }

    private GuestExcerptWithdrawnEvent parseAndValidate(String payload) {
        GuestExcerptWithdrawnEvent event;
        try {
            event = objectMapper.readValue(payload, GuestExcerptWithdrawnEvent.class);
        } catch (JsonProcessingException ex) {
            throw new BadEventException("Withdrawal event payload is not valid JSON", ex);
        }
        if (!GuestExcerptWithdrawnEvent.EVENT_TYPE.equals(event.eventType())) {
            throw new BadEventException("Unsupported eventType: " + event.eventType(), null);
        }
        if (event.eventVersion() != GuestExcerptWithdrawnEvent.EVENT_VERSION) {
            throw new BadEventException("Unsupported eventVersion: " + event.eventVersion(), null);
        }
        if (event.eventId() == null || event.excerptId() == null || event.releaseId() == null
                || event.episodeId() == null) {
            throw new BadEventException("Withdrawal event is missing required identifiers", null);
        }
        if (event.guestId() == null || event.guestId().isBlank()) {
            throw new BadEventException("Withdrawal event is missing the guest identifier", null);
        }
        if (event.reason() == null || event.reason().isBlank()) {
            throw new BadEventException("Withdrawal event is missing a reason", null);
        }
        return event;
    }

    static class BadEventException extends RuntimeException {
        BadEventException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
