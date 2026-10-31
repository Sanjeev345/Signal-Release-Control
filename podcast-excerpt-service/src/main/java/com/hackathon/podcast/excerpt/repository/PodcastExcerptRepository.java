package com.hackathon.podcast.excerpt.repository;

import com.hackathon.podcast.excerpt.domain.PodcastExcerpt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PodcastExcerptRepository extends JpaRepository<PodcastExcerpt, UUID> {

    List<PodcastExcerpt> findByEpisodeIdOrderByStartMsAsc(UUID episodeId);

    List<PodcastExcerpt> findByEpisodeIdAndGuestIdOrderByStartMsAsc(UUID episodeId, String guestId);

    @EntityGraph(attributePaths = "episode")
    Optional<PodcastExcerpt> findWithEpisodeById(UUID id);
}
