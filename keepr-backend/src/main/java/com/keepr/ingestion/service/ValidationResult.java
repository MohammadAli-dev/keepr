package com.keepr.ingestion.service;

/**
 * Record for capturing the result of a validation check.
 * 
 * @param valid       whether the validation passed
 * @param failureCode the short alphanumeric code describing the failure (null if valid)
 * @param reason      if invalid, a descriptive reason for the failure (null if valid)
 */
public record ValidationResult(boolean valid, String failureCode, String reason) {
    
    /**
     * Compact constructor to enforce state-safety.
     */
    public ValidationResult {
        if (!valid && (failureCode == null || failureCode.isBlank())) {
            throw new IllegalArgumentException("Failure code required for invalid status");
        }
        if (valid && (failureCode != null || reason != null)) {
            throw new IllegalArgumentException("Failure code and reason must be null on success");
        }
    }
    
    /**
     * Creates a successful ValidationResult.
     * Use this when all validation rules for a component are satisfied.
     *
     * @return a successful ValidationResult (valid=true, failureCode=null, reason=null)
     */
    public static ValidationResult success() {
        return new ValidationResult(true, null, null);
    }
    
    /**
     * Creates a failing ValidationResult with a specific code and reason.
     *
     * @param failureCode short alphanumeric failure code
     * @param reason the explanation for the validation failure
     * @return a failing ValidationResult (valid=false, failureCode=given code, reason=given reason)
     */
    public static ValidationResult failure(String failureCode, String reason) {
        return new ValidationResult(false, failureCode, reason);
    }
}
