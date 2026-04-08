package com.keepr.review.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "review_tasks")
@Getter
@Setter
public class ReviewTask {

    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(name = "raw_text", nullable = false, columnDefinition = "TEXT")
    private String rawText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extraction_json", nullable = false, columnDefinition = "JSONB")
    private Map<String, Object> extractionJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewTaskStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Creates a new ReviewTask instance.
     *
     * <p>Fields required for persistence (id, status, createdAt, updatedAt) are initialized by JPA lifecycle
     * callbacks when the entity is persisted or updated.</p>
     */
    public ReviewTask() {
    }

    /**
     * Initialize identity, timestamps, and default status before the entity is first persisted.
     *
     * If `id` is unset, assigns a new random UUID. Sets `createdAt` and `updatedAt` to the current
     * time. If `status` is unset, sets it to `ReviewTaskStatus.PENDING`.
     */
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (status == null) {
            status = ReviewTaskStatus.PENDING;
        }
    }

    /**
     * Sets the entity's `updatedAt` timestamp to the current time before an update.
     *
     * Invoked by the JPA provider as a `@PreUpdate` lifecycle callback to record when the entity was modified.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
