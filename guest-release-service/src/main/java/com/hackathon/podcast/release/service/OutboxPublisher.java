package com.hackathon.podcast.release.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.podcast.contracts.EventHeaders;
import com.hackathon.podcast.release.domain.EventOutbox;
import com.hackathon.podcast.release.repository.EventOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topic}")
    private String topic;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    public void publishPending() {
        List<EventOutbox> pending = outboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (EventOutbox outbox : pending) {
            publish(outbox);
        }
    }

    void publish(EventOutbox outbox) {
        try {
            JsonNode payload = objectMapper.readTree(outbox.getPayloadJson());
            String excerptId = payload.path("excerptId").asText();
            String correlationId = payload.path("correlationId").asText();

            ProducerRecord<String, String> record = new ProducerRecord<>(topic, excerptId, outbox.getPayloadJson());
            record.headers()
                    .add(EventHeaders.EVENT_TYPE, EventHeaders.EVENT_TYPE.getBytes(StandardCharsets.UTF_8))
                    .add(EventHeaders.EVENT_VERSION, "1".getBytes(StandardCharsets.UTF_8))
                    .add(EventHeaders.CORRELATION_ID, correlationId.getBytes(StandardCharsets.UTF_8))
                    .add(EventHeaders.PRODUCER, EventHeaders.EVENT_PRODUCER.getBytes(StandardCharsets.UTF_8));

            kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);

            outbox.setPublishedAt(Instant.now());
            outbox.setAttemptCount(outbox.getAttemptCount() + 1);
            outboxRepository.save(outbox);
            log.info("Outbox event published eventId={} excerptId={} correlationId={} topic={}",
                    outbox.getId(), excerptId, correlationId, topic);
        } catch (Exception ex) {
            outbox.setAttemptCount(outbox.getAttemptCount() + 1);
            outboxRepository.save(outbox);
            log.warn("Outbox publish failed eventId={} attempt={} error={}",
                    outbox.getId(), outbox.getAttemptCount(), ex.getMessage());
        }
    }
}
