package com.hackathon.podcast.excerpt.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.podcast.contracts.GuestExcerptWithdrawnEvent;
import com.hackathon.podcast.excerpt.domain.Episode;
import com.hackathon.podcast.excerpt.domain.NotificationEventType;
import com.hackathon.podcast.excerpt.domain.PodcastExcerpt;
import com.hackathon.podcast.excerpt.repository.PodcastExcerptRepository;
import com.hackathon.podcast.excerpt.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawalNotificationListenerTest {

    private static final String PRODUCER_ID = "11111111-1111-1111-1111-111111111111";

    @Mock
    ObjectMapper objectMapper;

    @Mock
    PodcastExcerptRepository excerptRepository;

    @Mock
    NotificationService notificationService;

    @InjectMocks
    WithdrawalNotificationListener listener;

    @Test
    void withdrawalIsRecordedForTheEpisodeProducer() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID excerptId = UUID.randomUUID();
        GuestExcerptWithdrawnEvent event = new GuestExcerptWithdrawnEvent(
                eventId,
                GuestExcerptWithdrawnEvent.EVENT_TYPE,
                GuestExcerptWithdrawnEvent.EVENT_VERSION,
                Instant.now(),
                "corr-1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                excerptId,
                "22222222-2222-2222-2222-222222222222",
                "22222222-2222-2222-2222-222222222222",
                "No longer comfortable");
        PodcastExcerpt excerpt = mock(PodcastExcerpt.class);
        Episode episode = mock(Episode.class);
        when(objectMapper.readValue(anyString(), eq(GuestExcerptWithdrawnEvent.class)))
                .thenReturn(event);
        when(excerptRepository.findWithEpisodeById(excerptId)).thenReturn(Optional.of(excerpt));
        when(excerpt.getEpisode()).thenReturn(episode);
        when(episode.getProducerId()).thenReturn(PRODUCER_ID);
        when(episode.getTitle()).thenReturn("Designing for Trust");
        when(excerpt.getId()).thenReturn(excerptId);
        when(excerpt.getCode()).thenReturn("ex-ava-020500");

        listener.onWithdrawal(record("{\"eventType\":\"GuestExcerptWithdrawn\"}"));

        verify(notificationService).recordExternal(
                eq(PRODUCER_ID),
                contains("ex-ava-020500"),
                eq(NotificationEventType.CONSENT_WITHDRAWN),
                eq(excerptId),
                eq(eventId));
    }

    @Test
    void unreadableEventsDoNotCreateNotifications() throws Exception {
        when(objectMapper.readValue(anyString(), eq(GuestExcerptWithdrawnEvent.class)))
                .thenThrow(new RuntimeException("bad json"));

        listener.onWithdrawal(record("not-json"));

        verify(notificationService, never()).recordExternal(
                anyString(), anyString(), eq(NotificationEventType.CONSENT_WITHDRAWN), eq(null), eq(null));
        verify(excerptRepository, never()).findWithEpisodeById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unknownExcerptsAreIgnored() throws Exception {
        UUID excerptId = UUID.randomUUID();
        GuestExcerptWithdrawnEvent event = new GuestExcerptWithdrawnEvent(
                UUID.randomUUID(),
                GuestExcerptWithdrawnEvent.EVENT_TYPE,
                GuestExcerptWithdrawnEvent.EVENT_VERSION,
                Instant.now(),
                "corr-1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                excerptId,
                "22222222-2222-2222-2222-222222222222",
                "22222222-2222-2222-2222-222222222222",
                "Reason");
        when(objectMapper.readValue(anyString(), eq(GuestExcerptWithdrawnEvent.class)))
                .thenReturn(event);
        when(excerptRepository.findWithEpisodeById(excerptId)).thenReturn(Optional.empty());

        listener.onWithdrawal(record("{}"));

        verify(notificationService, never()).recordExternal(
                anyString(), anyString(), eq(NotificationEventType.CONSENT_WITHDRAWN), eq(null), eq(null));
    }

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("soa.s14.t04.events", 0, 1L, "key", value);
    }
}
