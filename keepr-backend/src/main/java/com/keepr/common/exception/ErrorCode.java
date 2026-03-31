package com.keepr.common.exception;

/**
 * Enumeration of application error codes used across the Keepr platform.
 */
public enum ErrorCode {

    /** A generic internal server error. */
    INTERNAL_ERROR("KEEPR-500", "Internal server error"),

    /** The requested resource was not found. */
    NOT_FOUND("KEEPR-404", "Resource not found"),

    /** The request was invalid or malformed. */
    BAD_REQUEST("KEEPR-400", "Bad request"),

    /** The caller is not authorized to perform the action. */
    UNAUTHORIZED("KEEPR-401", "Unauthorized"),

    /** The caller lacks permissions for the requested resource. */
    FORBIDDEN("KEEPR-403", "Forbidden"),

    /** A duplicate resource already exists. */
    DUPLICATE("KEEPR-409", "Duplicate resource");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /**
     * Returns the machine-readable error code string.
     *
     * @return error code
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the default human-readable error message.
     *
     * @return default message
     */
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
