package com.hackathon.podcast.distribution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "distributed_edit")
@Getter
@Setter
@NoArgsConstructor
public class DistributedEdit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(name = "external_reference", nullable = false)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "distribution_status", nullable = false, length = 16)
    private DistributionStatus distributionStatus;

    @Column(name = "distributed_at", nullable = false)
    private Instant distributedAt;

    public static DistributedEdit create(String code, UUID episodeId, String provider,
                                         String externalReference, Instant distributedAt) {
        DistributedEdit edit = new DistributedEdit();
        edit.code = code;
        edit.episodeId = episodeId;
        edit.provider = provider;
        edit.externalReference = externalReference;
        edit.distributionStatus = DistributionStatus.DISTRIBUTED;
        edit.distributedAt = distributedAt;
        return edit;
    }
}
