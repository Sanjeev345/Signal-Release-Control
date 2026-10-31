package com.hackathon.podcast.excerpt.web.dto;

import com.hackathon.podcast.excerpt.domain.ExcerptRequest;

import java.time.Instant;
import java.util.UUID;

public record ExcerptRequestResponse(
        UUID id,
        UUID episodeId,
        String episodeTitle,
        String guestId,
        String title,
        String description,
        long startMs,
        long endMs,
        String status,
        String rejectionReason,
        UUID excerptId,
        String decidedBy,
        Instant decidedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static ExcerptRequestResponse from(ExcerptRequest request) {
        return new ExcerptRequestResponse(
                request.getId(),
                request.getEpisode().getId(),
                request.getEpisode().getTitle(),
                request.getGuestId(),
                request.getTitle(),
                request.getDescription(),
                request.getStartMs(),
                request.getEndMs(),
                request.getStatus().name(),
                request.getRejectionReason(),
                request.getExcerptId(),
                request.getDecidedBy(),
                request.getDecidedAt(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }
}
