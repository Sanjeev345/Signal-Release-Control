package com.hackathon.podcast.release.web.dto;

import com.hackathon.podcast.release.domain.GuestRelease;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReleaseResponse(
        UUID id,
        String guestId,
        UUID episodeId,
        String status,
        Instant grantedAt,
        Instant withdrawnAt,
        String withdrawnBy,
        String withdrawalReason,
        List<ScopeResponse> scopes
) {
    public static ReleaseResponse from(GuestRelease release, List<ScopeResponse> scopes) {
        return new ReleaseResponse(
                release.getId(),
                release.getGuestId(),
                release.getEpisodeId(),
                release.getStatus().name(),
                release.getGrantedAt(),
                release.getWithdrawnAt(),
                release.getWithdrawnBy(),
                release.getWithdrawalReason(),
                scopes);
    }
}
