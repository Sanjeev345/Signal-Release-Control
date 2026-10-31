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
 * Local read-model projection used for producer ownership checks in this demo.
 * It is seeded with the demo episode and never used as a cross-service database join.
 */
@Entity
@Table(name = "produced_episode")
@Getter
@Setter
@NoArgsConstructor
public class ProducedEpisode {

    @Id
    @Column(name = "episode_id")
    private UUID episodeId;

    @Column(name = "producer_id", nullable = false, length = 64)
    private String producerId;
}
