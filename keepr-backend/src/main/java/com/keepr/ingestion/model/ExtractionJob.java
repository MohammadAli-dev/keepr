package com.keepr.ingestion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity representing an asynchronous job for processing an uploaded document.
 */
@Entity
@Table(name = "extraction_jobs")
@Getter
@Setter
public class ExtractionJob {

    public ExtractionJob() {}

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID householdId;

    @Column(nullable = false)
    private UUID rawDocumentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(nullable = false)
    private int retryCount = 0;

    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;
    
    @Column(columnDefinition = "TEXT")
    private String rawText;

    private Double confidenceScore;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private java.util.Map<String, Object> extractionJson;

    private String failureReason;

    @Column(nullable = false)
    private int extractionVersion = 1;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private java.util.Map<String, Double> confidenceBreakdown;

    private Integer ocrMs;
    private Integer parseMs;
    private Integer validateMs;
    private Integer totalFieldsExtracted;
    private Integer successfulFields;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = JobStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
