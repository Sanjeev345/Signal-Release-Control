package com.hackathon.podcast.excerpt.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.podcast.contracts.GuestExcerptWithdrawnEvent;
import com.hackathon.podcast.excerpt.domain.NotificationEventType;
import com.hackathon.podcast.excerpt.domain.PodcastExcerpt;
import com.hackathon.podcast.excerpt.repository.PodcastExcerptRepository;
import com.hackathon.podcast.excerpt.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Notifies the episode producer when a guest withdraws consent for an excerpt.
 * Notification failures are logged and never block the withdrawal workflow:
 * the distribution service owns task creation on its own consumer group.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawalNotificationListener {

    private final ObjectMapper objectMapper;
    private final PodcastExcerptRepository excerptRepository;
    private final NotificationService notificationService;

    @KafkaListener(
            id = "excerpt-withdrawal-notification",
            topics = "${app.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onWithdrawal(ConsumerRecord<String, String> record) {
        GuestExcerptWithdrawnEvent event;
        try {
            event = objectMapper.readValue(record.value(), GuestExcerptWithdrawnEvent.class);
        } catch (Exception ex) {
            log.warn("Ignoring unreadable withdrawal notification key={} offset={} error={}",
                    record.key(), record.offset(), ex.getMessage());
            return;
        }
        if (!GuestExcerptWithdrawnEvent.EVENT_TYPE.equals(event.eventType())
                || event.eventId() == null || event.excerptId() == null) {
            return;
        }

        Optional<PodcastExcerpt> excerpt = excerptRepository.findWithEpisodeById(event.excerptId());
        if (excerpt.isEmpty()) {
            log.info("No local excerpt for withdrawal notification excerptId={} eventId={}",
                    event.excerptId(), event.eventId());
            return;
        }
        PodcastExcerpt local = excerpt.get();
        String message = "Guest " + event.guestId() + " withdrew consent for excerpt \""
                + local.getCode() + "\" in episode \"" + local.getEpisode().getTitle() + "\".";
        notificationService.recordExternal(
                local.getEpisode().getProducerId(),
                message,
                NotificationEventType.CONSENT_WITHDRAWN,
                local.getId(),
                event.eventId());
        log.info("Recorded withdrawal notification producerId={} excerptId={} eventId={}",
                local.getEpisode().getProducerId(), local.getId(), event.eventId());
    }
}
