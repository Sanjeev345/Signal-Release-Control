package com.hackathon.podcast.release.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Local content read model for validating release targets and rendering scope details.
 * Kept intentionally minimal and owned by this service (no shared database or entity model).
 */
@Entity
@Table(name = "excerpt_reference")
@Getter
@Setter
@NoArgsConstructor
public class ExcerptReference {

    @Id
    @Column(name = "excerpt_id")
    private UUID excerptId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "guest_id", nullable = false, length = 64)
    private String guestId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "start_ms", nullable = false)
    private long startMs;

    @Column(name = "end_ms", nullable = false)
    private long endMs;
}
