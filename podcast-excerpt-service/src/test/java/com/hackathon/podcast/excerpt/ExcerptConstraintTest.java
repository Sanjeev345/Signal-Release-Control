package com.hackathon.podcast.excerpt;

import com.hackathon.podcast.excerpt.domain.Episode;
import com.hackathon.podcast.excerpt.domain.PodcastExcerpt;
import com.hackathon.podcast.excerpt.repository.EpisodeRepository;
import com.hackathon.podcast.excerpt.repository.PodcastExcerptRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ExcerptConstraintTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    EpisodeRepository episodeRepository;

    @Autowired
    PodcastExcerptRepository excerptRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void rejectsExcerptWhoseEndIsNotAfterStart() {
        Episode episode = episodeRepository.saveAndFlush(
                Episode.create("ep-test-1", "Test", "producer-1", Instant.now()));
        PodcastExcerpt excerpt = PodcastExcerpt.create(
                episode, "ex-test-1", "guest-1", 2000, 1000, "invalid timing");

        assertThatThrownBy(() -> excerptRepository.saveAndFlush(excerpt))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsExcerptReferencingUnknownEpisode() {
        UUID unknownEpisode = UUID.randomUUID();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO podcast_excerpt (id, episode_id, code, guest_id, start_ms, end_ms, tag_status, created_at)
                VALUES (?, ?, 'ex-orphan', 'guest-1', 0, 1000, 'ACTIVE', now())
                """, UUID.randomUUID(), unknownEpisode))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void storesExcerptWithValidTiming() {
        Episode episode = episodeRepository.saveAndFlush(
                Episode.create("ep-test-2", "Test", "producer-1", Instant.now()));
        PodcastExcerpt excerpt = PodcastExcerpt.create(
                episode, "ex-test-2", "guest-1", 0, 1000, "valid");

        excerptRepository.saveAndFlush(excerpt);

        assertThat(excerptRepository.findByEpisodeIdOrderByStartMsAsc(episode.getId()))
                .extracting(PodcastExcerpt::getCode)
                .containsExactly("ex-test-2");
    }
}
