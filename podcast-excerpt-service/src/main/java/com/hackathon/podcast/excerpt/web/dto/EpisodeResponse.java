package com.hackathon.podcast.excerpt.web.dto;

import com.hackathon.podcast.excerpt.domain.Episode;

import java.time.Instant;
import java.util.UUID;

public record EpisodeResponse(
        UUID id,
        String code,
        String title,
        String producerId,
        Instant publishedAt,
        Instant createdAt
) {
    public static EpisodeResponse from(Episode episode) {
        return new EpisodeResponse(
                episode.getId(),
                episode.getCode(),
                episode.getTitle(),
                episode.getProducerId(),
                episode.getPublishedAt(),
                episode.getCreatedAt());
    }
}
