package com.keepr.ingestion.model;

/**
 * Enum defining the status transitions of an extraction job.
 */
public enum JobStatus {
    /**
     * Initial state, waiting to be picked up by a worker.
     */
    PENDING,

    /**
     * Currently being processed by a background worker.
     */
    PROCESSING,

    /**
     * Extraction and entity creation successfully finished.
     */
    COMPLETED,

    /**
     * Processing failed permanently after exceeding retry limits.
     */
    FAILED
}
