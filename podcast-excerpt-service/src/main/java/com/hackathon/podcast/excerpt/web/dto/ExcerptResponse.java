package com.hackathon.podcast.excerpt.web.dto;

import com.hackathon.podcast.excerpt.domain.PodcastExcerpt;

import java.time.Instant;
import java.util.UUID;

public record ExcerptResponse(
        UUID id,
        String code,
        UUID episodeId,
        String guestId,
        long startMs,
        long endMs,
        String transcript,
        String tagStatus,
        Instant createdAt
) {
    public static ExcerptResponse from(PodcastExcerpt excerpt) {
        return new ExcerptResponse(
                excerpt.getId(),
                excerpt.getCode(),
                excerpt.getEpisode().getId(),
                excerpt.getGuestId(),
                excerpt.getStartMs(),
                excerpt.getEndMs(),
                excerpt.getTranscript(),
                excerpt.getTagStatus().name(),
                excerpt.getCreatedAt());
    }
}
