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
    FAILED,

    /**
     * Extraction was successful but confidence is low. Manual review required.
     */
    REVIEW_REQUIRED,

    /**
     * User has successfully confirmed and completed the review task.
     */
    USER_CONFIRMED;

    /**
     * Checks if the current status is a terminal state (cannot be further processed by the worker).
     *
     * @return true if status is COMPLETED, FAILED, REVIEW_REQUIRED, or USER_CONFIRMED.
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == REVIEW_REQUIRED || this == USER_CONFIRMED;
    }
}
