package com.hackathon.podcast.excerpt.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateExcerptRequest(
        @NotBlank @Size(max = 64) String guestId,
        @NotNull @Min(0) Long startMs,
        @NotNull @Min(1) Long endMs,
        @Size(max = 20000) String transcript
) {
}
