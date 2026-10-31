package com.hackathon.podcast.excerpt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "podcast_episode")
@Getter
@Setter
@NoArgsConstructor
public class Episode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(name = "producer_id", nullable = false, length = 64)
    private String producerId;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public static Episode create(String code, String title, String producerId, Instant publishedAt) {
        Episode episode = new Episode();
        episode.code = code;
        episode.title = title;
        episode.producerId = producerId;
        episode.publishedAt = publishedAt;
        episode.createdAt = Instant.now();
        return episode;
    }
}
