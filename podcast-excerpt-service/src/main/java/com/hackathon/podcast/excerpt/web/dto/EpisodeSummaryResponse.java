package com.hackathon.podcast.excerpt.web.dto;

import com.hackathon.podcast.excerpt.domain.Episode;

import java.time.Instant;
import java.util.UUID;

public record EpisodeSummaryResponse(
        UUID id,
        String code,
        String title,
        Instant publishedAt
) {
    public static EpisodeSummaryResponse from(Episode episode) {
        return new EpisodeSummaryResponse(
                episode.getId(),
                episode.getCode(),
                episode.getTitle(),
                episode.getPublishedAt());
    }
}
