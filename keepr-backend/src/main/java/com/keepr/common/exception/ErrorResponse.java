package com.keepr.common.exception;

import java.time.Instant;

/**
 * Standard error response returned by all Keepr API endpoints on failure.
 *
 * @param code      machine-readable error code
 * @param message   human-readable error message
 * @param timestamp time of the error occurrence
 */
public record ErrorResponse(
        String code,
        String message,
        Instant timestamp
) {

    /**
     * Creates an ErrorResponse from a KeeprException.
     *
     * @param ex the exception to map
     * @return a new ErrorResponse
     */
    public static ErrorResponse from(KeeprException ex) {
        return new ErrorResponse(
                ex.getErrorCode().getCode(),
                ex.getMessage(),
                Instant.now()
        );
    }
}
