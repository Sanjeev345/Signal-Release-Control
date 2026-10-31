package com.hackathon.podcast.distribution.web.dto;

import com.hackathon.podcast.distribution.domain.TaskStatus;
import jakarta.validation.constraints.Size;

/**
 * Partial update for a task. A null status leaves the status unchanged, so notes and
 * evidence can be recorded without forcing a transition.
 */
public record UpdateTaskRequest(
        TaskStatus status,
        @Size(max = 4000) String notes,
        @Size(max = 2048) String evidenceUrl
) {
}
