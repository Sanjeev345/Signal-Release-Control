package com.hackathon.podcast.release.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "guest_release")
@Getter
@Setter
@NoArgsConstructor
public class GuestRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "guest_id", nullable = false, length = 64)
    private String guestId;

    @Column(name = "episode_id", nullable = false)
    private UUID episodeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReleaseStatus status;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "withdrawn_by", length = 64)
    private String withdrawnBy;

    @Column(name = "withdrawal_reason", columnDefinition = "text")
    private String withdrawalReason;

    @Version
    @Column(nullable = false)
    private long version;

    public static GuestRelease grant(String guestId, UUID episodeId, Instant grantedAt) {
        GuestRelease release = new GuestRelease();
        release.guestId = guestId;
        release.episodeId = episodeId;
        release.status = ReleaseStatus.ACTIVE;
        release.grantedAt = grantedAt;
        return release;
    }

    public void markWithdrawn(String reason, String actor, Instant at) {
        this.status = ReleaseStatus.WITHDRAWN;
        this.withdrawnAt = at;
        this.withdrawnBy = actor;
        this.withdrawalReason = reason;
    }
}
