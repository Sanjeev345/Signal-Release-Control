package com.hackathon.podcast.excerpt.repository;

import com.hackathon.podcast.excerpt.domain.Episode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EpisodeRepository extends JpaRepository<Episode, UUID> {

    List<Episode> findByProducerIdOrderByCreatedAtDesc(String producerId);

    Optional<Episode> findByIdAndProducerId(UUID id, String producerId);

    List<Episode> findByPublishedAtIsNullOrPublishedAtLessThanEqualOrderByPublishedAtDesc(Instant now);
}
