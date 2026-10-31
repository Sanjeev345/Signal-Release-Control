package com.hackathon.podcast.distribution.repository;

import com.hackathon.podcast.distribution.domain.ExcerptGuest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ExcerptGuestRepository extends JpaRepository<ExcerptGuest, UUID> {
}
