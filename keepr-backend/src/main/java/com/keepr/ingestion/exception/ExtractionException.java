package com.keepr.ingestion.exception;

import lombok.Getter;

/**
 * Custom exception for extraction-specific failures.
 * Carries a structured failure reason for persistence and analytics.
 */
@Getter
public class ExtractionException extends RuntimeException {
    
    private final String failureReason;

    public ExtractionException(String failureReason) {
        super(failureReason);
        this.failureReason = failureReason;
    }

    public ExtractionException(String failureReason, String message) {
        super(message);
        this.failureReason = failureReason;
    }
}
