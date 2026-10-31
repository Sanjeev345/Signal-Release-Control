package com.hackathon.podcast.release.repository;

import com.hackathon.podcast.release.domain.ExcerptReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ExcerptReferenceRepository extends JpaRepository<ExcerptReference, UUID> {
}
