package com.hackathon.podcast.release.web.dto;

import java.util.UUID;

public record WithdrawalAcceptedResponse(
        UUID eventId,
        UUID releaseId,
        UUID excerptId,
        String status
) {
    public static final String ACCEPTED = "WITHDRAWN_ACCEPTED";

    public static WithdrawalAcceptedResponse accepted(UUID eventId, UUID releaseId, UUID excerptId) {
        return new WithdrawalAcceptedResponse(eventId, releaseId, excerptId, ACCEPTED);
    }
}
