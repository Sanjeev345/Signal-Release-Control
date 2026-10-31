package com.hackathon.podcast.release.repository;

import com.hackathon.podcast.release.domain.ReleaseAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReleaseAuditRepository extends JpaRepository<ReleaseAudit, UUID> {

    List<ReleaseAudit> findByReleaseIdOrderByOccurredAtDesc(UUID releaseId);
}
