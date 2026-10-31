package com.hackathon.podcast.distribution.repository;

import com.hackathon.podcast.distribution.domain.DistributedEdit;
import com.hackathon.podcast.distribution.domain.DistributionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DistributedEditRepository extends JpaRepository<DistributedEdit, UUID> {

    List<DistributedEdit> findByEpisodeIdOrderByDistributedAtDesc(UUID episodeId);

    List<DistributedEdit> findByEpisodeIdIn(Collection<UUID> episodeIds);

    @Query("""
            select distinct e from DistributedEdit e, EditExcerpt ee
            where ee.id.editId = e.id
              and ee.id.excerptId = :excerptId
              and e.distributionStatus = :status
            order by e.code
            """)
    List<DistributedEdit> findDistributedContainingExcerpt(
            @Param("excerptId") UUID excerptId,
            @Param("status") DistributionStatus status);
}
