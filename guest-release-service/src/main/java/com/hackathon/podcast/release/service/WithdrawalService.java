package com.hackathon.podcast.release.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.podcast.contracts.GuestExcerptWithdrawnEvent;
import com.hackathon.podcast.release.domain.EventOutbox;
import com.hackathon.podcast.release.domain.GuestRelease;
import com.hackathon.podcast.release.domain.ReleaseAudit;
import com.hackathon.podcast.release.domain.ReleaseScope;
import com.hackathon.podcast.release.domain.ScopeStatus;
import com.hackathon.podcast.release.repository.EventOutboxRepository;
import com.hackathon.podcast.release.repository.GuestReleaseRepository;
import com.hackathon.podcast.release.repository.ReleaseAuditRepository;
import com.hackathon.podcast.release.repository.ReleaseScopeRepository;
import com.hackathon.podcast.release.web.dto.WithdrawalAcceptedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static com.hackathon.podcast.contracts.GuestExcerptWithdrawnEvent.EVENT_TYPE;
import static com.hackathon.podcast.contracts.GuestExcerptWithdrawnEvent.EVENT_VERSION;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final GuestReleaseRepository releaseRepository;
    private final ReleaseScopeRepository scopeRepository;
    private final EventOutboxRepository outboxRepository;
    private final ReleaseAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public WithdrawalAcceptedResponse withdraw(UUID releaseId, UUID excerptId, String reason,
                                               String guestId, String correlationId) {
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isEmpty()) {
            throw new BadRequestException("A withdrawal reason is required");
        }
        if (cleanReason.length() > 2000) {
            throw new BadRequestException("Withdrawal reason must be at most 2000 characters");
        }

        GuestRelease release = releaseRepository.findByIdForUpdate(releaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Release " + releaseId + " was not found"));
        if (!release.getGuestId().equals(guestId)) {
            throw new AccessDeniedException("Release belongs to another guest");
        }
        ReleaseScope scope = scopeRepository.findByReleaseIdAndExcerptId(releaseId, excerptId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Excerpt " + excerptId + " is not part of release " + releaseId));

        if (scope.getScopeStatus() == ScopeStatus.WITHDRAWN) {
            EventOutbox existing = outboxRepository
                    .findFirstByAggregateIdAndEventTypeOrderByCreatedAtAsc(scope.getId(), EVENT_TYPE)
                    .orElseThrow(() -> new IllegalStateException(
                            "Withdrawn scope " + scope.getId() + " has no withdrawal event"));
            log.info("Idempotent withdrawal replay releaseId={} excerptId={} eventId={}",
                    releaseId, excerptId, existing.getId());
            return WithdrawalAcceptedResponse.accepted(existing.getId(), releaseId, excerptId);
        }

        Instant now = Instant.now();
        scope.withdraw(cleanReason, guestId, now);
        scopeRepository.save(scope);

        long activeScopes = scopeRepository.countByReleaseIdAndScopeStatus(releaseId, ScopeStatus.ACTIVE);
        if (activeScopes == 0) {
            release.markWithdrawn(cleanReason, guestId, now);
            releaseRepository.save(release);
        }

        UUID eventId = UUID.randomUUID();
        String resolvedCorrelationId = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString()
                : correlationId;
        GuestExcerptWithdrawnEvent event = new GuestExcerptWithdrawnEvent(
                eventId,
                EVENT_TYPE,
                EVENT_VERSION,
                now,
                resolvedCorrelationId,
                releaseId,
                scope.getId(),
                release.getEpisodeId(),
                excerptId,
                release.getGuestId(),
                guestId,
                cleanReason);

        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxRepository.save(EventOutbox.create(
                    eventId, "ReleaseScope", scope.getId(), EVENT_TYPE, payload, now));
            auditRepository.save(ReleaseAudit.withdrawal(
                    releaseId, scope.getId(), excerptId, guestId, cleanReason, now,
                    eventId, resolvedCorrelationId));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize withdrawal event", ex);
        }

        log.info("Withdrawal accepted releaseId={} excerptId={} scopeId={} eventId={} correlationId={}",
                releaseId, excerptId, scope.getId(), eventId, event.correlationId());
        return WithdrawalAcceptedResponse.accepted(eventId, releaseId, excerptId);
    }
}
