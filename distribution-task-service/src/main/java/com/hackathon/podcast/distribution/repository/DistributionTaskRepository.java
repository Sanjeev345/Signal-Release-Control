package com.hackathon.podcast.distribution.repository;

import com.hackathon.podcast.distribution.domain.DistributionTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DistributionTaskRepository extends JpaRepository<DistributionTask, UUID> {

    List<DistributionTask> findByExcerptIdOrderByCreatedAtDesc(UUID excerptId);

    List<DistributionTask> findByEditIdInOrderByCreatedAtDesc(Collection<UUID> editIds);

    boolean existsByEventIdAndEditId(UUID eventId, UUID editId);

    long countByExcerptId(UUID excerptId);
}
