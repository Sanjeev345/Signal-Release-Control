package com.hackathon.podcast.excerpt.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateEpisodeRequest(
        @NotBlank @Size(max = 255) String title,
        Instant publishedAt
) {
}
