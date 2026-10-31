package com.hackathon.podcast.excerpt;

import com.hackathon.podcast.excerpt.domain.Episode;
import com.hackathon.podcast.excerpt.domain.ExcerptRequest;
import com.hackathon.podcast.excerpt.domain.NotificationEventType;
import com.hackathon.podcast.excerpt.repository.EpisodeRepository;
import com.hackathon.podcast.excerpt.repository.ExcerptRequestRepository;
import com.hackathon.podcast.excerpt.service.BadRequestException;
import com.hackathon.podcast.excerpt.service.ConflictException;
import com.hackathon.podcast.excerpt.service.ExcerptRequestService;
import com.hackathon.podcast.excerpt.service.ExcerptService;
import com.hackathon.podcast.excerpt.service.NotificationService;
import com.hackathon.podcast.excerpt.web.dto.CreateExcerptRequest;
import com.hackathon.podcast.excerpt.web.dto.ExcerptRequestResponse;
import com.hackathon.podcast.excerpt.web.dto.ExcerptResponse;
import com.hackathon.podcast.excerpt.web.dto.SubmitExcerptRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcerptRequestServiceTest {

    private static final UUID EPISODE_ID = UUID.fromString("2a3f078d-471e-46bb-a4b7-23ad33300960");
    private static final UUID EXCERPT_ID = UUID.fromString("0d3a4b4e-6a8b-4f61-9a02-58a4f5a6c7d8");
    private static final UUID REQUEST_ID = UUID.fromString("7f3e2b1a-9c8d-4e6f-8a1b-2c3d4e5f6071");
    private static final String PRODUCER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String GUEST_ID = "22222222-2222-2222-2222-222222222222";

    @Mock
    EpisodeRepository episodeRepository;

    @Mock
    ExcerptRequestRepository requestRepository;

    @Mock
    ExcerptService excerptService;

    @Mock
    NotificationService notificationService;

    ExcerptRequestService service;

    @BeforeEach
    void setUp() {
        service = new ExcerptRequestService(episodeRepository, requestRepository, excerptService, notificationService);
    }

    @Test
    void guestSubmissionCreatesPendingRequestWithoutConsentOrExcerpt() {
        when(episodeRepository.findById(EPISODE_ID)).thenReturn(Optional.of(episode(PRODUCER_ID)));
        when(requestRepository.save(any(ExcerptRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExcerptRequestResponse response = service.submit(
                new SubmitExcerptRequest(EPISODE_ID, "  Trust moment  ", "Why trust matters", 1000L, 2000L),
                GUEST_ID);

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.title()).isEqualTo("Trust moment");
        assertThat(response.guestId()).isEqualTo(GUEST_ID);
        assertThat(response.excerptId()).isNull();
        assertThat(response.decidedAt()).isNull();
        verifyNoInteractions(excerptService);
        verify(notificationService).notify(
                eq(PRODUCER_ID), contains("New excerpt request"), eq(NotificationEventType.REQUEST_SUBMITTED), any());
    }

    @Test
    void submissionWithEndNotAfterStartIsRejectedBeforePersisting() {
        when(episodeRepository.findById(EPISODE_ID)).thenReturn(Optional.of(episode(PRODUCER_ID)));

        assertThatThrownBy(() -> service.submit(
                new SubmitExcerptRequest(EPISODE_ID, "Title", null, 2000L, 2000L), GUEST_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("endMs must be greater than startMs");

        verify(requestRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void unknownEpisodeIsRejected() {
        when(episodeRepository.findById(EPISODE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(
                new SubmitExcerptRequest(EPISODE_ID, "Title", null, 0L, 1000L), GUEST_ID))
                .isInstanceOf(com.hackathon.podcast.excerpt.service.ResourceNotFoundException.class);
    }

    @Test
    void approvalUsesExistingExcerptCreationAndNotifiesGuest() {
        ExcerptRequest pending = ExcerptRequest.pending(
                episode(PRODUCER_ID), GUEST_ID, "Trust moment", "Why trust matters", 1000L, 2000L);
        when(requestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(pending));
        when(requestRepository.save(any(ExcerptRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(excerptService.createExcerpt(eq(EPISODE_ID), any(CreateExcerptRequest.class), eq(PRODUCER_ID)))
                .thenReturn(new ExcerptResponse(EXCERPT_ID, "ex-new", EPISODE_ID, GUEST_ID,
                        1000L, 2000L, "Trust moment\n\nWhy trust matters", "ACTIVE", Instant.now()));

        ExcerptRequestResponse response = service.approve(REQUEST_ID, PRODUCER_ID);

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.excerptId()).isEqualTo(EXCERPT_ID);
        assertThat(response.decidedBy()).isEqualTo(PRODUCER_ID);
        assertThat(response.decidedAt()).isNotNull();

        ArgumentCaptor<CreateExcerptRequest> captor = ArgumentCaptor.forClass(CreateExcerptRequest.class);
        verify(excerptService).createExcerpt(eq(EPISODE_ID), captor.capture(), eq(PRODUCER_ID));
        assertThat(captor.getValue().guestId()).isEqualTo(GUEST_ID);
        assertThat(captor.getValue().transcript()).contains("Trust moment").contains("Why trust matters");

        verify(notificationService).notify(
                eq(GUEST_ID), contains("approved"), eq(NotificationEventType.REQUEST_APPROVED), any());
    }

    @Test
    void rejectionRecordsReasonAndNotifiesGuest() {
        ExcerptRequest pending = ExcerptRequest.pending(
                episode(PRODUCER_ID), GUEST_ID, "Trust moment", null, 1000L, 2000L);
        when(requestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(pending));
        when(requestRepository.save(any(ExcerptRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExcerptRequestResponse response = service.reject(REQUEST_ID, "  Out of scope  ", PRODUCER_ID);

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.rejectionReason()).isEqualTo("Out of scope");
        assertThat(response.excerptId()).isNull();
        verifyNoInteractions(excerptService);
        verify(notificationService).notify(
                eq(GUEST_ID), contains("rejected"), eq(NotificationEventType.REQUEST_REJECTED), any());
    }

    @Test
    void producerCannotDecideAnotherProducersRequest() {
        ExcerptRequest pending = ExcerptRequest.pending(
                episode("99999999-9999-9999-9999-999999999999"), GUEST_ID, "Title", null, 0L, 1000L);
        when(requestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.approve(REQUEST_ID, PRODUCER_ID))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(excerptService, notificationService);
    }

    @Test
    void alreadyDecidedRequestCannotBeDecidedAgain() {
        ExcerptRequest pending = ExcerptRequest.pending(
                episode(PRODUCER_ID), GUEST_ID, "Title", null, 0L, 1000L);
        pending.approve(PRODUCER_ID, EXCERPT_ID, Instant.now());
        when(requestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.approve(REQUEST_ID, PRODUCER_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already APPROVED");
    }

    @Test
    void guestCannotReadAnotherGuestsRequest() {
        ExcerptRequest request = ExcerptRequest.pending(
                episode(PRODUCER_ID), "33333333-3333-3333-3333-333333333333", "Title", null, 0L, 1000L);
        when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.get(REQUEST_ID, GUEST_ID, false))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("another guest");
    }

    private Episode episode(String producerId) {
        Episode episode = Episode.create("ep-1", "Designing for Trust", producerId,
                Instant.parse("2026-10-20T09:00:00Z"));
        episode.setId(EPISODE_ID);
        return episode;
    }
}
