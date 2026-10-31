package com.hackathon.podcast.excerpt.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SubmitExcerptRequest(
        @NotNull UUID episodeId,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2000) String description,
        @NotNull @Min(0) Long startMs,
        @NotNull @Min(1) Long endMs
) {
}
