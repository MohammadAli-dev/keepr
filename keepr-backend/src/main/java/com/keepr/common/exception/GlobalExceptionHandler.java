package com.keepr.common.exception;

import java.time.Instant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler that translates exceptions into structured API responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles all {@link KeeprException} instances and maps them to appropriate HTTP responses.
     *
     * @param ex the KeeprException
     * @return structured error response with appropriate HTTP status
     */
    @ExceptionHandler(KeeprException.class)
    public ResponseEntity<ErrorResponse> handleKeeprException(KeeprException ex) {
        log.error("KeeprException: code={}, message={}", ex.getErrorCode().getCode(), ex.getMessage(), ex);
        ErrorResponse body = ErrorResponse.from(ex);
        HttpStatus status = mapToHttpStatus(ex.getErrorCode());
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Handles any unhandled exception as a 500 Internal Server Error.
     *
     * @param ex the unexpected exception
     * @return structured error response with 500 status
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        ErrorResponse body = new ErrorResponse(
                ErrorCode.INTERNAL_ERROR.getCode(),
                ErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private HttpStatus mapToHttpStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case DUPLICATE -> HttpStatus.CONFLICT;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
