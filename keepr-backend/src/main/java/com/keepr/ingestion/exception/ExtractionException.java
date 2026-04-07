package com.keepr.ingestion.exception;

/**
 * Custom exception for extraction-specific failures.
 * Carries a structured failure reason for persistence and analytics.
 */
public class ExtractionException extends RuntimeException {
    
    private final String failureReason;

    /**
     * Constructs a new ExtractionException with a specific failure reason.
     * The message is set to the failure reason string.
     *
     * @param failureReason machine-readable classification (e.g., LOW_CONFIDENCE)
     */
    public ExtractionException(String failureReason) {
        super(failureReason);
        this.failureReason = failureReason;
    }

    /**
     * Constructs a new ExtractionException with a failure reason and descriptive message.
     * Use this when a more detailed human-readable explanation is available.
     *
     * @param failureReason machine-readable classification
     * @param message       descriptive error message
     */
    public ExtractionException(String failureReason, String message) {
        super(message);
        this.failureReason = failureReason;
    }

    /**
     * Constructs a new ExtractionException with a failure reason, message, and cause.
     * Use this for exception chaining to preserve the original root cause.
     *
     * @param failureReason machine-readable classification
     * @param message       descriptive error message
     * @param cause         the original cause
     */
    public ExtractionException(String failureReason, String message, Throwable cause) {
        super(message, cause);
        this.failureReason = failureReason;
    }

    /**
     * Returns the structured failure reason for this exception.
     *
     * @return the failure reason string
     */
    public String getFailureReason() {
        return failureReason;
    }
}
