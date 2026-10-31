package com.hackathon.podcast.release;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hackathon.podcast.release.domain.EventOutbox;
import com.hackathon.podcast.release.domain.GuestRelease;
import com.hackathon.podcast.release.domain.ReleaseAudit;
import com.hackathon.podcast.release.domain.ReleaseScope;
import com.hackathon.podcast.release.domain.ReleaseStatus;
import com.hackathon.podcast.release.domain.ScopeStatus;
import com.hackathon.podcast.release.domain.ScopeType;
import com.hackathon.podcast.release.repository.EventOutboxRepository;
import com.hackathon.podcast.release.repository.GuestReleaseRepository;
import com.hackathon.podcast.release.repository.ReleaseAuditRepository;
import com.hackathon.podcast.release.repository.ReleaseScopeRepository;
import com.hackathon.podcast.release.service.WithdrawalService;
import com.hackathon.podcast.release.web.dto.WithdrawalAcceptedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestReleaseWithdrawalServiceTest {

    private static final UUID RELEASE_ID = UUID.fromString("d76025ba-7021-47f0-ab83-4749a581fd61");
    private static final UUID EPISODE_ID = UUID.fromString("2a3f078d-471e-46bb-a4b7-23ad33300960");
    private static final UUID TARGET_EXCERPT = UUID.fromString("f0f4e4bf-dff7-433c-91be-b8b151c47e42");
    private static final UUID OTHER_EXCERPT = UUID.fromString("6f0d5a2e-7c34-4b8b-9c31-2d1b7a9e3410");
    private static final String GUEST_ID = "22222222-2222-2222-2222-222222222222";

    @Mock
    GuestReleaseRepository releaseRepository;

    @Mock
    ReleaseScopeRepository scopeRepository;

    @Mock
    EventOutboxRepository outboxRepository;

    @Mock
    ReleaseAuditRepository auditRepository;

    ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    WithdrawalService withdrawalService;

    GuestRelease release;
    ReleaseScope targetScope;
    ReleaseScope siblingScope;

    @BeforeEach
    void setUp() {
        withdrawalService = new WithdrawalService(releaseRepository, scopeRepository, outboxRepository,
                auditRepository, objectMapper);
        release = GuestRelease.grant(GUEST_ID, EPISODE_ID, Instant.parse("2026-10-21T10:00:00Z"));
        release.setId(RELEASE_ID);
        targetScope = ReleaseScope.grant(release, TARGET_EXCERPT, ScopeType.PODCAST_DISTRIBUTION,
                Instant.parse("2026-10-21T10:00:00Z"));
        targetScope.setId(UUID.fromString("4ace15d0-2e39-488e-8bc1-4084a08d557a"));
        siblingScope = ReleaseScope.grant(release, OTHER_EXCERPT, ScopeType.PODCAST_DISTRIBUTION,
                Instant.parse("2026-10-21T10:00:00Z"));
        siblingScope.setId(UUID.fromString("9b1e2f3a-4c5d-4e6f-8a7b-1c2d3e4f5a60"));
    }

    @Test
    void withdrawOneScope_createsEventAndLeavesSiblingScopeActive() throws Exception {
        when(releaseRepository.findByIdForUpdate(RELEASE_ID)).thenReturn(Optional.of(release));
        when(scopeRepository.findByReleaseIdAndExcerptId(RELEASE_ID, TARGET_EXCERPT))
                .thenReturn(Optional.of(targetScope));
        when(scopeRepository.countByReleaseIdAndScopeStatus(RELEASE_ID, ScopeStatus.ACTIVE)).thenReturn(1L);
        when(outboxRepository.save(any(EventOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WithdrawalAcceptedResponse response = withdrawalService.withdraw(
                RELEASE_ID, TARGET_EXCERPT, "I no longer consent to this excerpt.",
                GUEST_ID, "demo-withdrawal-001");

        assertThat(response.status()).isEqualTo("WITHDRAWN_ACCEPTED");
        assertThat(response.releaseId()).isEqualTo(RELEASE_ID);
        assertThat(response.excerptId()).isEqualTo(TARGET_EXCERPT);
        assertThat(targetScope.getScopeStatus()).isEqualTo(ScopeStatus.WITHDRAWN);
        assertThat(targetScope.getWithdrawalReason()).isEqualTo("I no longer consent to this excerpt.");
        assertThat(siblingScope.getScopeStatus()).isEqualTo(ScopeStatus.ACTIVE);
        assertThat(release.getStatus()).isEqualTo(ReleaseStatus.ACTIVE);

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
        verify(outboxRepository, times(1)).save(captor.capture());
        EventOutbox outbox = captor.getValue();
        assertThat(outbox.getId()).isEqualTo(response.eventId());
        assertThat(outbox.getAggregateId()).isEqualTo(targetScope.getId());
        assertThat(outbox.getPublishedAt()).isNull();

        JsonNode payload = objectMapper.readTree(outbox.getPayloadJson());
        assertThat(payload.get("eventId").asText()).isEqualTo(response.eventId().toString());
        assertThat(payload.get("eventType").asText()).isEqualTo("GuestExcerptWithdrawn");
        assertThat(payload.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(payload.get("excerptId").asText()).isEqualTo(TARGET_EXCERPT.toString());
        assertThat(payload.get("releaseId").asText()).isEqualTo(RELEASE_ID.toString());
        assertThat(payload.get("releaseScopeId").asText()).isEqualTo(targetScope.getId().toString());
        assertThat(payload.get("guestId").asText()).isEqualTo(GUEST_ID);
        assertThat(payload.get("withdrawnBy").asText()).isEqualTo(GUEST_ID);
        assertThat(payload.get("correlationId").asText()).isEqualTo("demo-withdrawal-001");
        assertThat(payload.get("reason").asText()).isEqualTo("I no longer consent to this excerpt.");
        assertThat(payload.has("transcript")).isFalse();

        ArgumentCaptor<ReleaseAudit> auditCaptor = ArgumentCaptor.forClass(ReleaseAudit.class);
        verify(auditRepository, times(1)).save(auditCaptor.capture());
        ReleaseAudit audit = auditCaptor.getValue();
        assertThat(audit.getReleaseId()).isEqualTo(RELEASE_ID);
        assertThat(audit.getScopeId()).isEqualTo(targetScope.getId());
        assertThat(audit.getExcerptId()).isEqualTo(TARGET_EXCERPT);
        assertThat(audit.getAction()).isEqualTo("WITHDRAWAL");
        assertThat(audit.getActorId()).isEqualTo(GUEST_ID);
        assertThat(audit.getReason()).isEqualTo("I no longer consent to this excerpt.");
        assertThat(audit.getEventId()).isEqualTo(response.eventId());
        assertThat(audit.getCorrelationId()).isEqualTo("demo-withdrawal-001");
    }

    @Test
    void repeatedWithdrawalReturnsExistingEventWithoutDuplicateWork() {
        targetScope.withdraw("already withdrawn", GUEST_ID, Instant.now());
        UUID existingEventId = UUID.randomUUID();
        EventOutbox existing = EventOutbox.create(existingEventId, "ReleaseScope", targetScope.getId(),
                "GuestExcerptWithdrawn", "{}", Instant.now());

        when(releaseRepository.findByIdForUpdate(RELEASE_ID)).thenReturn(Optional.of(release));
        when(scopeRepository.findByReleaseIdAndExcerptId(RELEASE_ID, TARGET_EXCERPT))
                .thenReturn(Optional.of(targetScope));
        when(outboxRepository.findFirstByAggregateIdAndEventTypeOrderByCreatedAtAsc(
                targetScope.getId(), "GuestExcerptWithdrawn")).thenReturn(Optional.of(existing));

        WithdrawalAcceptedResponse response = withdrawalService.withdraw(
                RELEASE_ID, TARGET_EXCERPT, "again", GUEST_ID, "corr-2");

        assertThat(response.eventId()).isEqualTo(existingEventId);
        verify(outboxRepository, never()).save(any());
        verify(auditRepository, never()).save(any());
        verify(scopeRepository, never()).save(any());
    }

    @Test
    void lastActiveScopeMarksParentReleaseWithdrawn() {
        when(releaseRepository.findByIdForUpdate(RELEASE_ID)).thenReturn(Optional.of(release));
        when(scopeRepository.findByReleaseIdAndExcerptId(RELEASE_ID, TARGET_EXCERPT))
                .thenReturn(Optional.of(targetScope));
        when(scopeRepository.countByReleaseIdAndScopeStatus(RELEASE_ID, ScopeStatus.ACTIVE)).thenReturn(0L);
        when(outboxRepository.save(any(EventOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        withdrawalService.withdraw(RELEASE_ID, TARGET_EXCERPT, "withdraw all that remains",
                GUEST_ID, "corr-3");

        assertThat(release.getStatus()).isEqualTo(ReleaseStatus.WITHDRAWN);
        assertThat(release.getWithdrawnAt()).isNotNull();
        assertThat(release.getWithdrawnBy()).isEqualTo(GUEST_ID);
    }
}
