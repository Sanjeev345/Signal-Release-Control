package com.hackathon.podcast.release.repository;

import com.hackathon.podcast.release.domain.ReleaseScope;
import com.hackathon.podcast.release.domain.ScopeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReleaseScopeRepository extends JpaRepository<ReleaseScope, UUID> {

    List<ReleaseScope> findByReleaseIdOrderByGrantedAtAsc(UUID releaseId);

    Optional<ReleaseScope> findByReleaseIdAndExcerptId(UUID releaseId, UUID excerptId);

    long countByReleaseIdAndScopeStatus(UUID releaseId, ScopeStatus scopeStatus);
}
