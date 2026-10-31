package com.hackathon.podcast.distribution.web.dto;

import com.hackathon.podcast.distribution.domain.DistributedEdit;
import com.hackathon.podcast.distribution.domain.DistributionTask;

import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID editId,
        String editCode,
        String provider,
        UUID excerptId,
        UUID eventId,
        String taskType,
        String status,
        String priority,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        String notes,
        String evidenceUrl,
        String updatedBy
) {
    public static TaskResponse from(DistributionTask task, DistributedEdit edit) {
        return new TaskResponse(
                task.getId(),
                task.getEditId(),
                edit == null ? null : edit.getCode(),
                edit == null ? null : edit.getProvider(),
                task.getExcerptId(),
                task.getEventId(),
                task.getTaskType().name(),
                task.getStatus().name(),
                task.getPriority().name(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getCompletedAt(),
                task.getNotes(),
                task.getEvidenceUrl(),
                task.getUpdatedBy());
    }
}
