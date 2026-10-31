package com.hackathon.podcast.release;

import com.hackathon.podcast.release.domain.EventOutbox;
import com.hackathon.podcast.release.domain.GuestRelease;
import com.hackathon.podcast.release.domain.ReleaseScope;
import com.hackathon.podcast.release.domain.ScopeType;
import com.hackathon.podcast.release.repository.EventOutboxRepository;
import com.hackathon.podcast.release.repository.GuestReleaseRepository;
import com.hackathon.podcast.release.repository.ReleaseScopeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReleaseConstraintTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    GuestReleaseRepository releaseRepository;

    @Autowired
    ReleaseScopeRepository scopeRepository;

    @Autowired
    EventOutboxRepository outboxRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void releaseScopeIsUniquePerReleaseAndExcerpt() {
        GuestRelease release = releaseRepository.saveAndFlush(
                GuestRelease.grant("guest-1", UUID.randomUUID(), Instant.now()));
        UUID excerptId = UUID.randomUUID();
        scopeRepository.saveAndFlush(ReleaseScope.grant(release, excerptId, ScopeType.PODCAST_DISTRIBUTION, Instant.now()));

        ReleaseScope duplicate = ReleaseScope.grant(release, excerptId, ScopeType.PODCAST_DISTRIBUTION, Instant.now());

        assertThatThrownBy(() -> scopeRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void outboxIsUniquePerAggregateAndEventType() {
        UUID aggregateId = UUID.randomUUID();
        outboxRepository.saveAndFlush(EventOutbox.create(UUID.randomUUID(), "ReleaseScope", aggregateId,
                "GuestExcerptWithdrawn", "{}", Instant.now()));

        EventOutbox duplicate = EventOutbox.create(UUID.randomUUID(), "ReleaseScope", aggregateId,
                "GuestExcerptWithdrawn", "{}", Instant.now());

        assertThatThrownBy(() -> outboxRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void releaseScopeRequiresExistingRelease() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO release_scope (id, release_id, excerpt_id, scope_type, scope_status, granted_at, version)
                VALUES (?, ?, ?, 'PODCAST_DISTRIBUTION', 'ACTIVE', now(), 0)
                """, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
