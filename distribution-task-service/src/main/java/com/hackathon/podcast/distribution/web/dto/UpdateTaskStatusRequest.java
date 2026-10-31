package com.hackathon.podcast.distribution.web.dto;

import com.hackathon.podcast.distribution.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTaskStatusRequest(@NotNull TaskStatus status) {
}
