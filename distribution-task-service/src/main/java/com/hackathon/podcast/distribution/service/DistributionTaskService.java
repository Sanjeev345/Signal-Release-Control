package com.hackathon.podcast.distribution.service;

import com.hackathon.podcast.contracts.GuestExcerptWithdrawnEvent;
import com.hackathon.podcast.distribution.domain.DistributedEdit;
import com.hackathon.podcast.distribution.domain.DistributionStatus;
import com.hackathon.podcast.distribution.domain.DistributionTask;
import com.hackathon.podcast.distribution.domain.EpisodeOwner;
import com.hackathon.podcast.distribution.domain.ExcerptGuest;
import com.hackathon.podcast.distribution.domain.TaskStatus;
import com.hackathon.podcast.distribution.messaging.TaskNotificationPublisher;
import com.hackathon.podcast.distribution.repository.DistributedEditRepository;
import com.hackathon.podcast.distribution.repository.DistributionTaskRepository;
import com.hackathon.podcast.distribution.repository.EpisodeOwnerRepository;
import com.hackathon.podcast.distribution.repository.ExcerptGuestRepository;
import com.hackathon.podcast.distribution.repository.ProcessedEventRepository;
import com.hackathon.podcast.distribution.web.dto.EditResponse;
import com.hackathon.podcast.distribution.web.dto.TaskResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributionTaskService {

    private final DistributedEditRepository editRepository;
    private final DistributionTaskRepository taskRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final EpisodeOwnerRepository episodeOwnerRepository;
    private final ExcerptGuestRepository excerptGuestRepository;
    private final TaskNotificationPublisher taskNotificationPublisher;

    /**
     * Atomically claims the event and creates one OPEN/HIGH removal task per distributed edit.
     * Replays return 0 and never create duplicate tasks.
     */
    @Transactional
    public int processWithdrawal(GuestExcerptWithdrawnEvent event, String correlationId) {
        ExcerptGuest reference = excerptGuestRepository.findById(event.excerptId())
                .orElseThrow(() -> new InvalidWithdrawalEventException(
                        "Excerpt " + event.excerptId() + " is not in the distribution projection"));
        if (!reference.getEpisodeId().equals(event.episodeId())) {
            throw new InvalidWithdrawalEventException("Event episode " + event.episodeId()
                    + " does not match excerpt episode " + reference.getEpisodeId());
        }
        if (!reference.getGuestId().equals(event.guestId())) {
            throw new InvalidWithdrawalEventException(
                    "Event guest " + event.guestId() + " does not own excerpt " + event.excerptId());
        }

        Instant now = Instant.now();
        if (processedEventRepository.tryClaim(event.eventId(), event.eventType(), now) == 0) {
            log.info("Duplicate withdrawal event ignored eventId={} excerptId={} correlationId={}",
                    event.eventId(), event.excerptId(), correlationId);
            return 0;
        }

        List<DistributedEdit> edits = editRepository.findDistributedContainingExcerpt(
                event.excerptId(), DistributionStatus.DISTRIBUTED);
        int created = 0;
        for (DistributedEdit edit : edits) {
            if (taskRepository.existsByEventIdAndEditId(event.eventId(), edit.getId())) {
                continue;
            }
            taskRepository.save(DistributionTask.openRemoval(
                    edit.getId(), event.excerptId(), event.eventId(), now));
            created++;
        }

        log.info("Withdrawal workflow persisted eventId={} excerptId={} affectedEdits={} correlationId={}",
                event.eventId(), event.excerptId(), created, correlationId);
        return created;
    }

    @Transactional(readOnly = true)
    public List<EditResponse> listEdits(UUID episodeId, String producerId) {
        requireProducerOwnedEpisode(episodeId, producerId);
        List<DistributedEdit> edits = editRepository.findByEpisodeIdOrderByDistributedAtDesc(episodeId);
        if (edits.isEmpty()) {
            return List.of();
        }
        Map<UUID, DistributedEdit> editsById = edits.stream()
                .collect(Collectors.toMap(DistributedEdit::getId, edit -> edit));
        Map<UUID, List<TaskResponse>> tasksByEdit = taskRepository
                .findByEditIdInOrderByCreatedAtDesc(editsById.keySet())
                .stream()
                .collect(Collectors.groupingBy(DistributionTask::getEditId,
                        Collectors.mapping(
                                task -> TaskResponse.from(task, editsById.get(task.getEditId())),
                                Collectors.toList())));
        return edits.stream()
                .map(edit -> EditResponse.from(edit, tasksByEdit.getOrDefault(edit.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listTasks(UUID excerptId, String subject, boolean producer) {
        ExcerptGuest reference = excerptGuestRepository.findById(excerptId)
                .orElseThrow(() -> new ResourceNotFoundException("Excerpt " + excerptId + " was not found"));

        if (producer) {
            requireProducerOwnedEpisode(reference.getEpisodeId(), subject);
        } else if (!reference.getGuestId().equals(subject)) {
            throw new AccessDeniedException("Excerpt belongs to another guest");
        }

        List<DistributionTask> tasks = taskRepository.findByExcerptIdOrderByCreatedAtDesc(excerptId);
        Map<UUID, DistributedEdit> editsById = editRepository.findAllById(
                        tasks.stream().map(DistributionTask::getEditId).distinct().toList())
                .stream().collect(Collectors.toMap(DistributedEdit::getId, edit -> edit));
        return tasks.stream()
                .map(task -> TaskResponse.from(task, editsById.get(task.getEditId())))
                .toList();
    }

    /**
     * Producer view across owned episodes, optionally narrowed by episode, platform and status.
     */
    @Transactional(readOnly = true)
    public List<TaskResponse> listTasksForProducer(String producerId, UUID episodeId,
                                                   String provider, TaskStatus status) {
        List<UUID> episodeIds;
        if (episodeId != null) {
            requireProducerOwnedEpisode(episodeId, producerId);
            episodeIds = List.of(episodeId);
        } else {
            episodeIds = episodeOwnerRepository.findByProducerId(producerId).stream()
                    .map(EpisodeOwner::getEpisodeId)
                    .toList();
        }
        if (episodeIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, DistributedEdit> editsById = editRepository.findByEpisodeIdIn(episodeIds).stream()
                .collect(Collectors.toMap(DistributedEdit::getId, edit -> edit));
        if (editsById.isEmpty()) {
            return List.of();
        }

        return taskRepository.findByEditIdInOrderByCreatedAtDesc(editsById.keySet()).stream()
                .filter(task -> status == null || task.getStatus() == status)
                .filter(task -> provider == null || provider.isBlank()
                        || editsById.get(task.getEditId()).getProvider().equalsIgnoreCase(provider))
                .map(task -> TaskResponse.from(task, editsById.get(task.getEditId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(UUID taskId, String subject, boolean producer) {
        TaskContext context = loadTask(taskId);
        if (producer) {
            requireProducerOwnedEpisode(context.edit().getEpisodeId(), subject);
        } else {
            ExcerptGuest reference = excerptGuestRepository.findById(context.task().getExcerptId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Excerpt " + context.task().getExcerptId() + " was not found"));
            if (!reference.getGuestId().equals(subject)) {
                throw new AccessDeniedException("Excerpt belongs to another guest");
            }
        }
        return TaskResponse.from(context.task(), context.edit());
    }

    @Transactional
    public TaskResponse updateTask(UUID taskId, TaskStatus status, String notes,
                                   String evidenceUrl, String producerId) {
        if (status == null && notes == null && evidenceUrl == null) {
            throw new BadRequestException("Supply a status, notes or evidenceUrl to update a task");
        }
        String cleanNotes = normalize(notes);
        String cleanEvidence = normalize(evidenceUrl);
        if (cleanEvidence != null) {
            requireHttpUrl(cleanEvidence);
        }

        TaskContext context = loadOwnedTask(taskId, producerId);
        DistributionTask task = context.task();

        boolean statusChanged = status != null && status != task.getStatus();
        if (statusChanged && !task.canTransitionTo(status)) {
            throw new ConflictException(
                    "Task cannot transition from " + task.getStatus() + " to " + status);
        }

        Instant now = Instant.now();
        if (cleanNotes != null || cleanEvidence != null) {
            task.updateMetadata(cleanNotes, cleanEvidence, producerId, now);
        }
        if (statusChanged) {
            task.transitionTo(status, now, producerId);
        }
        DistributionTask saved = taskRepository.save(task);

        if (statusChanged && status == TaskStatus.DONE) {
            excerptGuestRepository.findById(saved.getExcerptId()).ifPresent(reference ->
                    taskNotificationPublisher.publishTaskCompleted(
                            saved, context.edit(), context.edit().getEpisodeId(),
                            reference.getGuestId(), producerId));
        }
        return TaskResponse.from(saved, context.edit());
    }

    @Transactional
    public TaskResponse updateTaskStatus(UUID taskId, TaskStatus status, String producerId) {
        return updateTask(taskId, status, null, null, producerId);
    }

    private TaskContext loadOwnedTask(UUID taskId, String producerId) {
        TaskContext context = loadTask(taskId);
        requireProducerOwnedEpisode(context.edit().getEpisodeId(), producerId);
        return context;
    }

    private TaskContext loadTask(UUID taskId) {
        DistributionTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task " + taskId + " was not found"));
        DistributedEdit edit = editRepository.findById(task.getEditId())
                .orElseThrow(() -> new IllegalStateException(
                        "Task " + taskId + " references missing edit " + task.getEditId()));
        return new TaskContext(task, edit);
    }

    private void requireProducerOwnedEpisode(UUID episodeId, String producerId) {
        EpisodeOwner owner = episodeOwnerRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode " + episodeId + " was not found"));
        if (!owner.getProducerId().equals(producerId)) {
            throw new AccessDeniedException("Episode belongs to another producer");
        }
    }

    private static void requireHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException("unsupported scheme");
            }
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("evidenceUrl must be an absolute http(s) URL");
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record TaskContext(DistributionTask task, DistributedEdit edit) {
    }
}
