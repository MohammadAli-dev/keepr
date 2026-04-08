package com.keepr.common.exception;

import java.time.Instant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.web.bind.MethodArgumentNotValidException;
import java.util.stream.Collectors;
import java.util.List;

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
     * Handles Bean Validation failures and returns detailed field-level errors.
     *
     * @param ex the validation exception
     * @return structured 400 response with field error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        log.error("Validation failed: {}", ex.getBindingResult().getAllErrors());
        
        List<ErrorResponse.ValidationError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> new ErrorResponse.ValidationError(err.getField(), err.getDefaultMessage()))
                .toList();

        String combinedMessage = fieldErrors.stream()
                .map(e -> e.field() + ": " + e.message())
                .collect(Collectors.joining("; "));

        ErrorResponse body = new ErrorResponse(
                ErrorCode.BAD_REQUEST.getCode(),
                combinedMessage,
                Instant.now(),
                fieldErrors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
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
                Instant.now(),
                null
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
            case CONFLICT -> HttpStatus.CONFLICT;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
