package com.hackathon.podcast.excerpt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "podcast_excerpt")
@Getter
@Setter
@NoArgsConstructor
public class PodcastExcerpt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "guest_id", nullable = false, length = 64)
    private String guestId;

    @Column(name = "start_ms", nullable = false)
    private long startMs;

    @Column(name = "end_ms", nullable = false)
    private long endMs;

    @Column(columnDefinition = "text")
    private String transcript;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag_status", nullable = false, length = 16)
    private TagStatus tagStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public static PodcastExcerpt create(Episode episode, String code, String guestId,
                                        long startMs, long endMs, String transcript) {
        PodcastExcerpt excerpt = new PodcastExcerpt();
        excerpt.episode = episode;
        excerpt.code = code;
        excerpt.guestId = guestId;
        excerpt.startMs = startMs;
        excerpt.endMs = endMs;
        excerpt.transcript = transcript;
        excerpt.tagStatus = TagStatus.ACTIVE;
        excerpt.createdAt = Instant.now();
        return excerpt;
    }
}
