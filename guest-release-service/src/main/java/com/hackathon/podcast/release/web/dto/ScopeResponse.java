package com.hackathon.podcast.release.web.dto;

import com.hackathon.podcast.release.domain.ReleaseScope;

import java.time.Instant;
import java.util.UUID;

public record ScopeResponse(
        UUID id,
        UUID excerptId,
        String excerptCode,
        Long startMs,
        Long endMs,
        String scopeType,
        String status,
        Instant grantedAt,
        Instant withdrawnAt,
        String withdrawnBy,
        String withdrawalReason
) {
    public static ScopeResponse from(ReleaseScope scope, String excerptCode, Long startMs, Long endMs) {
        return new ScopeResponse(
                scope.getId(),
                scope.getExcerptId(),
                excerptCode,
                startMs,
                endMs,
                scope.getScopeType().name(),
                scope.getScopeStatus().name(),
                scope.getGrantedAt(),
                scope.getWithdrawnAt(),
                scope.getWithdrawnBy(),
                scope.getWithdrawalReason());
    }
}
