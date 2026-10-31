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
@Table(name = "excerpt_guest")
@Getter
@Setter
@NoArgsConstructor
public class ExcerptGuest {

    @Id
    @Column(name = "excerpt_id")
    private UUID excerptId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(name = "guest_id", nullable = false, length = 64)
    private String guestId;
}
