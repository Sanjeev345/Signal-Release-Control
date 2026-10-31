package com.hackathon.podcast.distribution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "distribution_task",
        uniqueConstraints = @UniqueConstraint(name = "uq_task_event_edit", columnNames = {"event_id", "edit_id"}))
@Getter
@Setter
@NoArgsConstructor
public class DistributionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "edit_id", nullable = false)
    private UUID editId;

    @Column(name = "excerpt_id", nullable = false)
    private UUID excerptId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 32)
    private TaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskPriority priority;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "evidence_url", length = 2048)
    private String evidenceUrl;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Version
    @Column(nullable = false)
    private long version;

    public boolean canTransitionTo(TaskStatus next) {
        return (status == TaskStatus.OPEN && next == TaskStatus.IN_PROGRESS)
                || (status == TaskStatus.IN_PROGRESS && next == TaskStatus.DONE);
    }

    public void transitionTo(TaskStatus next, Instant at, String actor) {
        this.status = next;
        this.updatedAt = at;
        this.updatedBy = actor;
        if (next == TaskStatus.DONE) {
            this.completedAt = at;
        }
    }

    public void updateMetadata(String notes, String evidenceUrl, String actor, Instant at) {
        if (notes != null) {
            this.notes = notes;
        }
        if (evidenceUrl != null) {
            this.evidenceUrl = evidenceUrl;
        }
        this.updatedBy = actor;
        this.updatedAt = at;
    }

    public static DistributionTask openRemoval(UUID editId, UUID excerptId, UUID eventId, Instant now) {
        DistributionTask task = new DistributionTask();
        task.editId = editId;
        task.excerptId = excerptId;
        task.eventId = eventId;
        task.taskType = TaskType.EXCERPT_REMOVAL;
        task.status = TaskStatus.OPEN;
        task.priority = TaskPriority.HIGH;
        task.createdAt = now;
        task.updatedAt = now;
        return task;
    }
}
