package com.hackathon.podcast.distribution.web.dto;

import com.hackathon.podcast.distribution.domain.DistributedEdit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EditResponse(
        UUID id,
        String code,
        UUID episodeId,
        String provider,
        String externalReference,
        String distributionStatus,
        Instant distributedAt,
        List<TaskResponse> tasks
) {
    public static EditResponse from(DistributedEdit edit, List<TaskResponse> tasks) {
        return new EditResponse(
                edit.getId(),
                edit.getCode(),
                edit.getEpisodeId(),
                edit.getProvider(),
                edit.getExternalReference(),
                edit.getDistributionStatus().name(),
                edit.getDistributedAt(),
                tasks);
    }
}
