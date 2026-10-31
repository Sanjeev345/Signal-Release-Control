package com.hackathon.podcast.distribution.repository;

import com.hackathon.podcast.distribution.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    /**
     * Atomically claims an event. Concurrent deliveries of the same event both attempt this insert;
     * exactly one gets 1, the other gets 0 and is treated as a duplicate.
     */
    @Modifying
    @Query(value = """
            insert into processed_event (event_id, event_type, processed_at)
            values (:eventId, :eventType, :processedAt)
            on conflict (event_id) do nothing
            """, nativeQuery = true)
    int tryClaim(@Param("eventId") UUID eventId,
                 @Param("eventType") String eventType,
                 @Param("processedAt") Instant processedAt);
}
