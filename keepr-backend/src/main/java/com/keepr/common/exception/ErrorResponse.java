package com.keepr.common.exception;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response returned by all Keepr API endpoints on failure.
 *
 * @param code        machine-readable error code
 * @param message     human-readable error message
 * @param timestamp   time of the error occurrence
 * @param fieldErrors optional list of granular validation errors
 */
public record ErrorResponse(
        String code,
        String message,
        Instant timestamp,
        List<ValidationError> fieldErrors
) {

    /**
     * Nested record representing a single field validation failure.
     */
    public record ValidationError(String field, String message) {}

    /**
     * Creates an ErrorResponse from a KeeprException without field errors.
     *
     * @param ex the exception to map
     * @return a new ErrorResponse
     */
    public static ErrorResponse from(KeeprException ex) {
        return new ErrorResponse(
                ex.getErrorCode().getCode(),
                ex.getMessage(),
                Instant.now(),
                null
        );
    }
}
