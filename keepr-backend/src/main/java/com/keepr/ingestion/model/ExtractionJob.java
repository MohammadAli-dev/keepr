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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.Map;
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

    /**
     * Stores a human-readable summary of the error, often including the exception 
     * message or a snippet of the stack trace for debugging purposes.
     */
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;
    
    @Column(columnDefinition = "TEXT")
    private String rawText;

    private Double confidenceScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> extractionJson;

    /**
     * A structured, machine-readable classification of the failure (e.g., LOW_CONFIDENCE,
     * INVALID_DEVICE). Used for automated reporting, analytics, and UI messaging.
     */
    private String failureReason;

    @Column(nullable = false)
    private int extractionVersion = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Double> confidenceBreakdown;

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
