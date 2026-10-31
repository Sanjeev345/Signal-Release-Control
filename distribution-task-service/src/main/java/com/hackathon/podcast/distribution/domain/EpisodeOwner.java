package com.hackathon.podcast.distribution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "episode_owner")
@Getter
@Setter
@NoArgsConstructor
public class EpisodeOwner {

    @Id
    @Column(name = "episode_id")
    private UUID episodeId;

    @Column(name = "producer_id", nullable = false, length = 64)
    private String producerId;
}
