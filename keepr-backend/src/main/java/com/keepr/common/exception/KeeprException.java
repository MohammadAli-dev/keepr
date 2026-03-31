package com.keepr.common.exception;

/**
 * Custom exception type for all Keepr application errors.
 * Carries an {@link ErrorCode} for structured error handling.
 */
public class KeeprException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * Constructs a KeeprException with an error code and message.
     *
     * @param errorCode the structured error code
     * @param message   the detail message
     */
    public KeeprException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Constructs a KeeprException with an error code, message, and cause.
     *
     * @param errorCode the structured error code
     * @param message   the detail message
     * @param cause     the underlying cause
     */
    public KeeprException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Returns the error code associated with this exception.
     *
     * @return the error code
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
