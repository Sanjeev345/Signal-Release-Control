package com.hackathon.podcast.distribution.repository;

import com.hackathon.podcast.distribution.domain.EpisodeOwner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EpisodeOwnerRepository extends JpaRepository<EpisodeOwner, UUID> {

    List<EpisodeOwner> findByProducerId(String producerId);
}
