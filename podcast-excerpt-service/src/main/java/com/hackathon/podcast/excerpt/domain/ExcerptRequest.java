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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "excerpt_request")
@Getter
@NoArgsConstructor
public class ExcerptRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;

    @Column(name = "guest_id", nullable = false, length = 64)
    private String guestId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "start_ms", nullable = false)
    private long startMs;

    @Column(name = "end_ms", nullable = false)
    private long endMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExcerptRequestStatus status;

    @Column(name = "rejection_reason", length = 2000)
    private String rejectionReason;

    @Column(name = "decided_by", length = 64)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "excerpt_id")
    private UUID excerptId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ExcerptRequest pending(Episode episode, String guestId, String title,
                                         String description, long startMs, long endMs) {
        Instant now = Instant.now();
        ExcerptRequest request = new ExcerptRequest();
        request.episode = episode;
        request.guestId = guestId;
        request.title = title;
        request.description = description;
        request.startMs = startMs;
        request.endMs = endMs;
        request.status = ExcerptRequestStatus.PENDING;
        request.createdAt = now;
        request.updatedAt = now;
        return request;
    }

    public void approve(String producerId, UUID excerptId, Instant at) {
        this.status = ExcerptRequestStatus.APPROVED;
        this.decidedBy = producerId;
        this.decidedAt = at;
        this.excerptId = excerptId;
        this.rejectionReason = null;
        this.updatedAt = at;
    }

    public void reject(String producerId, String reason, Instant at) {
        this.status = ExcerptRequestStatus.REJECTED;
        this.decidedBy = producerId;
        this.decidedAt = at;
        this.rejectionReason = reason;
        this.updatedAt = at;
    }
}
