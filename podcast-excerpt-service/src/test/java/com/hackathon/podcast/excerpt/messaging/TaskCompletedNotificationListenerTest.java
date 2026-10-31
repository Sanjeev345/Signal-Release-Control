package com.hackathon.podcast.excerpt.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.podcast.contracts.DistributionTaskCompletedEvent;
import com.hackathon.podcast.excerpt.domain.NotificationEventType;
import com.hackathon.podcast.excerpt.repository.PodcastExcerptRepository;
import com.hackathon.podcast.excerpt.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskCompletedNotificationListenerTest {

    private static final String GUEST_ID = "22222222-2222-2222-2222-222222222222";

    @Mock
    ObjectMapper objectMapper;

    @Mock
    PodcastExcerptRepository excerptRepository;

    @Mock
    NotificationService notificationService;

    @InjectMocks
    TaskCompletedNotificationListener listener;

    @Test
    void completionIsRecordedForTheGuestWithoutClaimingExternalRemoval() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        DistributionTaskCompletedEvent event = event(
                eventId, taskId, GUEST_ID, DistributionTaskCompletedEvent.EVENT_TYPE);
        when(objectMapper.readValue(anyString(), eq(DistributionTaskCompletedEvent.class)))
                .thenReturn(event);

        listener.onTaskCompleted(record("{}"));

        verify(notificationService).recordExternal(
                eq(GUEST_ID),
                contains("does not confirm removal"),
                eq(NotificationEventType.TASK_COMPLETED),
                eq(taskId),
                eq(eventId));
    }

    @Test
    void nonCompletionEventTypesAreIgnored() throws Exception {
        DistributionTaskCompletedEvent event = event(
                UUID.randomUUID(), UUID.randomUUID(), GUEST_ID, "SomethingElse");
        when(objectMapper.readValue(anyString(), eq(DistributionTaskCompletedEvent.class)))
                .thenReturn(event);

        listener.onTaskCompleted(record("{}"));

        verify(notificationService, never()).recordExternal(
                anyString(), anyString(), eq(NotificationEventType.TASK_COMPLETED), eq(null), eq(null));
    }

    @Test
    void eventsWithoutAGuestAreIgnored() throws Exception {
        DistributionTaskCompletedEvent event = event(
                UUID.randomUUID(), UUID.randomUUID(), " ", DistributionTaskCompletedEvent.EVENT_TYPE);
        when(objectMapper.readValue(anyString(), eq(DistributionTaskCompletedEvent.class)))
                .thenReturn(event);

        listener.onTaskCompleted(record("{}"));

        verify(notificationService, never()).recordExternal(
                anyString(), anyString(), eq(NotificationEventType.TASK_COMPLETED), eq(null), eq(null));
    }

    private static DistributionTaskCompletedEvent event(
            UUID eventId, UUID taskId, String guestId, String eventType) {
        return new DistributionTaskCompletedEvent(
                eventId,
                eventType,
                DistributionTaskCompletedEvent.EVENT_VERSION,
                Instant.now(),
                "corr-1",
                taskId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                guestId,
                "11111111-1111-1111-1111-111111111111",
                "EXCERPT_REMOVAL",
                "YouTube",
                Instant.now());
    }

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("soa.s14.t04.notifications", 0, 1L, "key", value);
    }
}
