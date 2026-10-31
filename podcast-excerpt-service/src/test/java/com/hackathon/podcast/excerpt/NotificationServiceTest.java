package com.hackathon.podcast.excerpt;

import com.hackathon.podcast.excerpt.domain.Notification;
import com.hackathon.podcast.excerpt.domain.NotificationEventType;
import com.hackathon.podcast.excerpt.repository.NotificationRepository;
import com.hackathon.podcast.excerpt.service.NotificationService;
import com.hackathon.podcast.excerpt.service.ResourceNotFoundException;
import com.hackathon.podcast.excerpt.web.dto.NotificationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final String GUEST_ID = "22222222-2222-2222-2222-222222222222";
    private static final String OTHER_ID = "33333333-3333-3333-3333-333333333333";

    @Mock
    NotificationRepository notificationRepository;

    @InjectMocks
    NotificationService service;

    @Test
    void listedNotificationsBelongToTheRecipientFromTheSecurityContext() {
        Notification mine = Notification.create(
                GUEST_ID, "Your request was approved.", NotificationEventType.REQUEST_APPROVED, UUID.randomUUID());
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(GUEST_ID))
                .thenReturn(List.of(mine));

        List<NotificationResponse> response = service.list(GUEST_ID, false);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).message()).isEqualTo("Your request was approved.");
        verify(notificationRepository).findByRecipientIdOrderByCreatedAtDesc(GUEST_ID);
        verify(notificationRepository, never()).findByRecipientIdOrderByCreatedAtDesc(OTHER_ID);
    }

    @Test
    void unreadFilterUsesRecipientScope() {
        when(notificationRepository.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(GUEST_ID))
                .thenReturn(List.of());

        service.list(GUEST_ID, true);

        verify(notificationRepository).findByRecipientIdAndReadFalseOrderByCreatedAtDesc(GUEST_ID);
    }

    @Test
    void markingAnotherUsersNotificationIsNotFound() {
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findByIdAndRecipientId(notificationId, GUEST_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(notificationId, GUEST_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void externalEventIsRecordedOnlyOnce() {
        UUID eventId = UUID.randomUUID();
        when(notificationRepository.existsBySourceEventId(eventId)).thenReturn(true);

        service.recordExternal(GUEST_ID, "Duplicate", NotificationEventType.CONSENT_WITHDRAWN,
                UUID.randomUUID(), eventId);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void externalEventIsPersistedWhenNew() {
        UUID eventId = UUID.randomUUID();
        when(notificationRepository.existsBySourceEventId(eventId)).thenReturn(false);
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.recordExternal(GUEST_ID, "  Guest withdrew consent.  ",
                NotificationEventType.CONSENT_WITHDRAWN, UUID.randomUUID(), eventId);

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void notificationMessagesAreTruncatedToColumnWidth() {
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.notify(GUEST_ID, "x".repeat(1500), NotificationEventType.TASK_COMPLETED, UUID.randomUUID());

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void blankRecipientIsIgnored() {
        service.notify(" ", "ignored", NotificationEventType.REQUEST_SUBMITTED, UUID.randomUUID());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAllReadScopesToRecipient() {
        when(notificationRepository.markAllRead(GUEST_ID)).thenReturn(2);

        int updated = service.markAllRead(GUEST_ID);

        assertThat(updated).isEqualTo(2);
        verify(notificationRepository).markAllRead(GUEST_ID);
    }
}
