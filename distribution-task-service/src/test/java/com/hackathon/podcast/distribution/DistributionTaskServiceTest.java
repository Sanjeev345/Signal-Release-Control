package com.hackathon.podcast.distribution;

import com.hackathon.podcast.contracts.GuestExcerptWithdrawnEvent;
import com.hackathon.podcast.distribution.domain.DistributedEdit;
import com.hackathon.podcast.distribution.domain.DistributionStatus;
import com.hackathon.podcast.distribution.domain.DistributionTask;
import com.hackathon.podcast.distribution.domain.EpisodeOwner;
import com.hackathon.podcast.distribution.domain.ExcerptGuest;
import com.hackathon.podcast.distribution.domain.TaskPriority;
import com.hackathon.podcast.distribution.domain.TaskStatus;
import com.hackathon.podcast.distribution.domain.TaskType;
import com.hackathon.podcast.distribution.messaging.TaskNotificationPublisher;
import com.hackathon.podcast.distribution.repository.DistributedEditRepository;
import com.hackathon.podcast.distribution.repository.DistributionTaskRepository;
import com.hackathon.podcast.distribution.repository.EpisodeOwnerRepository;
import com.hackathon.podcast.distribution.repository.ExcerptGuestRepository;
import com.hackathon.podcast.distribution.repository.ProcessedEventRepository;
import com.hackathon.podcast.distribution.service.BadRequestException;
import com.hackathon.podcast.distribution.service.ConflictException;
import com.hackathon.podcast.distribution.service.DistributionTaskService;
import com.hackathon.podcast.distribution.service.InvalidWithdrawalEventException;
import com.hackathon.podcast.distribution.service.ResourceNotFoundException;
import com.hackathon.podcast.distribution.web.dto.TaskResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistributionTaskServiceTest {

    private static final UUID TARGET_EXCERPT = UUID.fromString("f0f4e4bf-dff7-433c-91be-b8b151c47e42");
    private static final UUID EDIT_A = UUID.fromString("e0000000-0000-4000-8000-000000000001");
    private static final UUID EDIT_B = UUID.fromString("e0000000-0000-4000-8000-000000000002");
    private static final UUID EDIT_C = UUID.fromString("e0000000-0000-4000-8000-000000000003");
    private static final UUID EVENT_ID = UUID.fromString("1ac7c51a-5f7a-4b0a-9fc9-9ba4bef605f0");
    private static final UUID TASK_ID = UUID.fromString("8b7f0f22-2a5e-4a94-9e6e-f1e3c7782f10");
    private static final UUID EPISODE_ID = UUID.fromString("2a3f078d-471e-46bb-a4b7-23ad33300960");
    private static final String PRODUCER_ID = "11111111-1111-1111-1111-111111111111";

    @Mock
    DistributedEditRepository editRepository;

    @Mock
    DistributionTaskRepository taskRepository;

    @Mock
    ProcessedEventRepository processedEventRepository;

    @Mock
    EpisodeOwnerRepository episodeOwnerRepository;

    @Mock
    ExcerptGuestRepository excerptGuestRepository;

    @Mock
    TaskNotificationPublisher taskNotificationPublisher;

    DistributionTaskService service;

    GuestExcerptWithdrawnEvent event;

    @BeforeEach
    void setUp() {
        service = new DistributionTaskService(editRepository, taskRepository, processedEventRepository,
                episodeOwnerRepository, excerptGuestRepository, taskNotificationPublisher);
        event = new GuestExcerptWithdrawnEvent(
                EVENT_ID,
                "GuestExcerptWithdrawn",
                1,
                Instant.parse("2026-10-27T09:30:00Z"),
                "demo-withdrawal-001",
                UUID.fromString("d76025ba-7021-47f0-ab83-4749a581fd61"),
                UUID.fromString("4ace15d0-2e39-488e-8bc1-4084a08d557a"),
                UUID.fromString("2a3f078d-471e-46bb-a4b7-23ad33300960"),
                TARGET_EXCERPT,
                "22222222-2222-2222-2222-222222222222",
                "22222222-2222-2222-2222-222222222222",
                "I no longer consent to this excerpt.");
    }

    @Test
    void withdrawal_createsTaskForEveryDistributedEditContainingExcerpt() {
        DistributedEdit editA = edit(EDIT_A, "edit-youtube-001");
        DistributedEdit editB = edit(EDIT_B, "edit-spotify-001");
        when(excerptGuestRepository.findById(TARGET_EXCERPT)).thenReturn(Optional.of(reference()));
        when(processedEventRepository.tryClaim(any(), any(), any())).thenReturn(1);
        when(editRepository.findDistributedContainingExcerpt(TARGET_EXCERPT, DistributionStatus.DISTRIBUTED))
                .thenReturn(List.of(editA, editB));
        when(taskRepository.existsByEventIdAndEditId(EVENT_ID, EDIT_A)).thenReturn(false);
        when(taskRepository.existsByEventIdAndEditId(EVENT_ID, EDIT_B)).thenReturn(false);
        when(taskRepository.save(any(DistributionTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int created = service.processWithdrawal(event, "demo-withdrawal-001");

        assertThat(created).isEqualTo(2);
        ArgumentCaptor<DistributionTask> captor = ArgumentCaptor.forClass(DistributionTask.class);
        verify(taskRepository, times(2)).save(captor.capture());
        List<DistributionTask> tasks = captor.getAllValues();
        assertThat(tasks).extracting(DistributionTask::getEditId)
                .containsExactlyInAnyOrder(EDIT_A, EDIT_B)
                .doesNotContain(EDIT_C);
        assertThat(tasks).allSatisfy(task -> {
            assertThat(task.getExcerptId()).isEqualTo(TARGET_EXCERPT);
            assertThat(task.getEventId()).isEqualTo(EVENT_ID);
            assertThat(task.getTaskType()).isEqualTo(TaskType.EXCERPT_REMOVAL);
            assertThat(task.getStatus()).isEqualTo(TaskStatus.OPEN);
            assertThat(task.getPriority()).isEqualTo(TaskPriority.HIGH);
        });
        verify(processedEventRepository).tryClaim(eq(EVENT_ID), eq("GuestExcerptWithdrawn"), any(Instant.class));
    }

    @Test
    void duplicateEventCreatesNoAdditionalTasks() {
        when(excerptGuestRepository.findById(TARGET_EXCERPT)).thenReturn(Optional.of(reference()));
        when(processedEventRepository.tryClaim(any(), any(), any())).thenReturn(0);

        int created = service.processWithdrawal(event, "demo-withdrawal-001");

        assertThat(created).isZero();
        verify(taskRepository, never()).save(any());
        verify(editRepository, never()).findDistributedContainingExcerpt(any(), any());
    }

    @Test
    void eventForUnknownExcerptIsRejectedBeforeAnyWrite() {
        when(excerptGuestRepository.findById(TARGET_EXCERPT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.processWithdrawal(event, "demo-withdrawal-001"))
                .isInstanceOf(InvalidWithdrawalEventException.class)
                .hasMessageContaining("not in the distribution projection");
        verify(processedEventRepository, never()).tryClaim(any(), any(), any());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void eventWithMismatchedEpisodeOrGuestIsRejected() {
        ExcerptGuest reference = reference();
        reference.setEpisodeId(UUID.fromString("99999999-9999-9999-9999-999999999999"));
        when(excerptGuestRepository.findById(TARGET_EXCERPT)).thenReturn(Optional.of(reference));

        assertThatThrownBy(() -> service.processWithdrawal(event, "demo-withdrawal-001"))
                .isInstanceOf(InvalidWithdrawalEventException.class)
                .hasMessageContaining("does not match excerpt episode");

        ExcerptGuest otherGuest = reference();
        otherGuest.setGuestId("33333333-3333-3333-3333-333333333333");
        when(excerptGuestRepository.findById(TARGET_EXCERPT)).thenReturn(Optional.of(otherGuest));

        assertThatThrownBy(() -> service.processWithdrawal(event, "demo-withdrawal-001"))
                .isInstanceOf(InvalidWithdrawalEventException.class)
                .hasMessageContaining("does not own excerpt");
        verify(processedEventRepository, never()).tryClaim(any(), any(), any());
    }

    @Test
    void validEventWithNoMatchingEditsIsClaimedWithoutTasks() {
        when(excerptGuestRepository.findById(TARGET_EXCERPT)).thenReturn(Optional.of(reference()));
        when(processedEventRepository.tryClaim(any(), any(), any())).thenReturn(1);
        when(editRepository.findDistributedContainingExcerpt(TARGET_EXCERPT, DistributionStatus.DISTRIBUTED))
                .thenReturn(List.of());

        int created = service.processWithdrawal(event, "demo-withdrawal-001");

        assertThat(created).isZero();
        verify(taskRepository, never()).save(any());
        verify(processedEventRepository).tryClaim(eq(EVENT_ID), eq("GuestExcerptWithdrawn"), any(Instant.class));
    }

    @Test
    void producerCanAdvanceOpenTaskToInProgressThenDone() {
        DistributedEdit edit = edit(EDIT_A, "edit-youtube-001");
        DistributionTask task = DistributionTask.openRemoval(EDIT_A, TARGET_EXCERPT, EVENT_ID,
                Instant.parse("2026-10-29T12:00:00Z"));
        task.setId(TASK_ID);
        EpisodeOwner owner = owner(PRODUCER_ID);

        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(editRepository.findById(EDIT_A)).thenReturn(Optional.of(edit));
        when(episodeOwnerRepository.findById(EPISODE_ID)).thenReturn(Optional.of(owner));
        when(taskRepository.save(any(DistributionTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse inProgress = service.updateTaskStatus(TASK_ID, TaskStatus.IN_PROGRESS, PRODUCER_ID);
        assertThat(inProgress.status()).isEqualTo("IN_PROGRESS");
        assertThat(inProgress.completedAt()).isNull();

        TaskResponse done = service.updateTaskStatus(TASK_ID, TaskStatus.DONE, PRODUCER_ID);
        assertThat(done.status()).isEqualTo("DONE");
        assertThat(done.completedAt()).isNotNull();
        assertThat(done.editCode()).isEqualTo("edit-youtube-001");
    }

    @Test
    void invalidTaskTransitionIsRejected() {
        DistributedEdit edit = edit(EDIT_A, "edit-youtube-001");
        DistributionTask task = DistributionTask.openRemoval(EDIT_A, TARGET_EXCERPT, EVENT_ID,
                Instant.parse("2026-10-29T12:00:00Z"));
        task.setId(TASK_ID);

        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(editRepository.findById(EDIT_A)).thenReturn(Optional.of(edit));
        when(episodeOwnerRepository.findById(EPISODE_ID)).thenReturn(Optional.of(owner(PRODUCER_ID)));

        assertThatThrownBy(() -> service.updateTaskStatus(TASK_ID, TaskStatus.DONE, PRODUCER_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("OPEN")
                .hasMessageContaining("DONE");
        verify(taskRepository, never()).save(any());
    }

    @Test
    void producerCannotUpdateTaskForAnotherProducersEpisode() {
        DistributedEdit edit = edit(EDIT_A, "edit-youtube-001");
        DistributionTask task = DistributionTask.openRemoval(EDIT_A, TARGET_EXCERPT, EVENT_ID,
                Instant.parse("2026-10-29T12:00:00Z"));
        task.setId(TASK_ID);

        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(editRepository.findById(EDIT_A)).thenReturn(Optional.of(edit));
        when(episodeOwnerRepository.findById(EPISODE_ID)).thenReturn(Optional.of(owner("other-producer")));

        assertThatThrownBy(() -> service.updateTaskStatus(TASK_ID, TaskStatus.IN_PROGRESS, PRODUCER_ID))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void unknownTaskIsNotFound() {
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateTaskStatus(TASK_ID, TaskStatus.IN_PROGRESS, PRODUCER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void completionRecordsEvidenceNotesAndActorAndNotifiesGuest() {
        DistributedEdit edit = edit(EDIT_A, "edit-youtube-001");
        DistributionTask task = DistributionTask.openRemoval(EDIT_A, TARGET_EXCERPT, EVENT_ID,
                Instant.parse("2026-10-29T12:00:00Z"));
        task.setId(TASK_ID);
        task.transitionTo(TaskStatus.IN_PROGRESS, Instant.parse("2026-10-29T12:10:00Z"), PRODUCER_ID);

        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(editRepository.findById(EDIT_A)).thenReturn(Optional.of(edit));
        when(episodeOwnerRepository.findById(EPISODE_ID)).thenReturn(Optional.of(owner(PRODUCER_ID)));
        when(taskRepository.save(any(DistributionTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(excerptGuestRepository.findById(TARGET_EXCERPT)).thenReturn(Optional.of(reference()));

        TaskResponse response = service.updateTask(TASK_ID, TaskStatus.DONE,
                "  Removed the clip  ", "https://evidence.example.com/youtube/removal-001", PRODUCER_ID);

        assertThat(response.status()).isEqualTo("DONE");
        assertThat(response.completedAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
        assertThat(response.notes()).isEqualTo("Removed the clip");
        assertThat(response.evidenceUrl()).isEqualTo("https://evidence.example.com/youtube/removal-001");
        assertThat(response.updatedBy()).isEqualTo(PRODUCER_ID);
        verify(taskNotificationPublisher).publishTaskCompleted(
                any(DistributionTask.class), eq(edit), eq(EPISODE_ID),
                eq("22222222-2222-2222-2222-222222222222"), eq(PRODUCER_ID));
    }

    @Test
    void metadataOnlyUpdateKeepsStatusAndDoesNotNotify() {
        DistributedEdit edit = edit(EDIT_A, "edit-youtube-001");
        DistributionTask task = DistributionTask.openRemoval(EDIT_A, TARGET_EXCERPT, EVENT_ID,
                Instant.parse("2026-10-29T12:00:00Z"));
        task.setId(TASK_ID);

        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(editRepository.findById(EDIT_A)).thenReturn(Optional.of(edit));
        when(episodeOwnerRepository.findById(EPISODE_ID)).thenReturn(Optional.of(owner(PRODUCER_ID)));
        when(taskRepository.save(any(DistributionTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = service.updateTask(TASK_ID, null, "Waiting on platform", null, PRODUCER_ID);

        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.notes()).isEqualTo("Waiting on platform");
        assertThat(response.updatedBy()).isEqualTo(PRODUCER_ID);
        assertThat(response.completedAt()).isNull();
        verifyNoInteractions(taskNotificationPublisher);
    }

    @Test
    void invalidEvidenceUrlIsRejectedBeforeLoadingTheTask() {
        assertThatThrownBy(() -> service.updateTask(TASK_ID, TaskStatus.IN_PROGRESS,
                null, "ftp://evidence.example.com/file", PRODUCER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("http");

        verify(taskRepository, never()).findById(any());
    }

    @Test
    void updateWithoutAnyChangeIsRejected() {
        assertThatThrownBy(() -> service.updateTask(TASK_ID, null, null, null, PRODUCER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Supply a status");
        verify(taskRepository, never()).findById(any());
    }

    @Test
    void invalidCompletionTransitionDoesNotPersistMetadata() {
        DistributedEdit edit = edit(EDIT_A, "edit-youtube-001");
        DistributionTask task = DistributionTask.openRemoval(EDIT_A, TARGET_EXCERPT, EVENT_ID,
                Instant.parse("2026-10-29T12:00:00Z"));
        task.setId(TASK_ID);

        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(editRepository.findById(EDIT_A)).thenReturn(Optional.of(edit));
        when(episodeOwnerRepository.findById(EPISODE_ID)).thenReturn(Optional.of(owner(PRODUCER_ID)));

        assertThatThrownBy(() -> service.updateTask(TASK_ID, TaskStatus.DONE,
                "Skipped ahead", "https://evidence.example.com/x", PRODUCER_ID))
                .isInstanceOf(ConflictException.class);
        verify(taskRepository, never()).save(any());
        verifyNoInteractions(taskNotificationPublisher);
    }

    private ExcerptGuest reference() {
        ExcerptGuest reference = new ExcerptGuest();
        reference.setExcerptId(TARGET_EXCERPT);
        reference.setEpisodeId(EPISODE_ID);
        reference.setGuestId("22222222-2222-2222-2222-222222222222");
        return reference;
    }

    private EpisodeOwner owner(String producerId) {
        EpisodeOwner owner = new EpisodeOwner();
        owner.setEpisodeId(EPISODE_ID);
        owner.setProducerId(producerId);
        return owner;
    }

    private DistributedEdit edit(UUID id, String code) {
        DistributedEdit edit = DistributedEdit.create(code,
                UUID.fromString("2a3f078d-471e-46bb-a4b7-23ad33300960"),
                "provider", code, Instant.now());
        edit.setId(id);
        return edit;
    }
}
