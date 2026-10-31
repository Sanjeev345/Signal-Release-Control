package com.hackathon.podcast.release.repository;

import com.hackathon.podcast.release.domain.ProducedEpisode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProducedEpisodeRepository extends JpaRepository<ProducedEpisode, UUID> {
}
