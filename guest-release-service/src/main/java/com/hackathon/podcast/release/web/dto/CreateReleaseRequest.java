package com.hackathon.podcast.release.web.dto;

import com.hackathon.podcast.release.domain.ScopeType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateReleaseRequest(
        @NotNull UUID guestId,
        @NotNull UUID episodeId,
        @NotEmpty List<@NotNull UUID> excerptIds,
        ScopeType scopeType
) {
}
