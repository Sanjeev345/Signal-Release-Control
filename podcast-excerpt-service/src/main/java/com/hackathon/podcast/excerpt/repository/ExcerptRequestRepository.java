package com.hackathon.podcast.excerpt.repository;

import com.hackathon.podcast.excerpt.domain.ExcerptRequest;
import com.hackathon.podcast.excerpt.domain.ExcerptRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExcerptRequestRepository extends JpaRepository<ExcerptRequest, UUID> {

    List<ExcerptRequest> findByGuestIdOrderByCreatedAtDesc(String guestId);

    List<ExcerptRequest> findByGuestIdAndStatusOrderByCreatedAtDesc(String guestId, ExcerptRequestStatus status);

    List<ExcerptRequest> findByEpisodeProducerIdOrderByCreatedAtDesc(String producerId);

    List<ExcerptRequest> findByEpisodeProducerIdAndStatusOrderByCreatedAtDesc(
            String producerId, ExcerptRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ExcerptRequest r where r.id = :id")
    Optional<ExcerptRequest> findByIdForUpdate(@Param("id") UUID id);
}
