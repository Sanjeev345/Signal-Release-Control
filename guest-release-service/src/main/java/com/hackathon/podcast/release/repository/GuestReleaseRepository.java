package com.hackathon.podcast.release.repository;

import com.hackathon.podcast.release.domain.GuestRelease;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuestReleaseRepository extends JpaRepository<GuestRelease, UUID> {

    List<GuestRelease> findByGuestIdOrderByGrantedAtDesc(String guestId);

    List<GuestRelease> findByEpisodeIdOrderByGrantedAtDesc(UUID episodeId);

    /** Serializes withdrawals per release so scope counts and duplicate requests stay consistent. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from GuestRelease r where r.id = :id")
    Optional<GuestRelease> findByIdForUpdate(@Param("id") UUID id);
}
