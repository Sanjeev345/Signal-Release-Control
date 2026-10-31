package com.hackathon.podcast.release.domain;

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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "release_scope",
        uniqueConstraints = @UniqueConstraint(name = "uq_release_scope", columnNames = {"release_id", "excerpt_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ReleaseScope {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "release_id", nullable = false)
    private GuestRelease release;

    @Column(name = "excerpt_id", nullable = false)
    private UUID excerptId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 32)
    private ScopeType scopeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_status", nullable = false, length = 16)
    private ScopeStatus scopeStatus;

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

    public static ReleaseScope grant(GuestRelease release, UUID excerptId, ScopeType scopeType, Instant grantedAt) {
        ReleaseScope scope = new ReleaseScope();
        scope.release = release;
        scope.excerptId = excerptId;
        scope.scopeType = scopeType;
        scope.scopeStatus = ScopeStatus.ACTIVE;
        scope.grantedAt = grantedAt;
        return scope;
    }

    public void withdraw(String reason, String actor, Instant at) {
        this.scopeStatus = ScopeStatus.WITHDRAWN;
        this.withdrawnAt = at;
        this.withdrawnBy = actor;
        this.withdrawalReason = reason;
    }
}
