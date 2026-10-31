package com.hackathon.podcast.release.repository;

import com.hackathon.podcast.release.domain.EventOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventOutboxRepository extends JpaRepository<EventOutbox, UUID> {

    List<EventOutbox> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();

    Optional<EventOutbox> findFirstByAggregateIdAndEventTypeOrderByCreatedAtAsc(UUID aggregateId, String eventType);
}
