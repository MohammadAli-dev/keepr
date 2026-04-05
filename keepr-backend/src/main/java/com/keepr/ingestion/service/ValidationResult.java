package com.keepr.ingestion.service;

/**
 * Record for capturing the result of a validation check.
 * 
 * @param valid  whether the validation passed
 * @param reason if invalid, a descriptive reason for the failure (null if valid)
 */
public record ValidationResult(boolean valid, String reason) {
    
    /**
     * Creates a successful ValidationResult.
     * Use this when all validation rules for a component are satisfied.
     *
     * @return a successful ValidationResult (valid=true, reason=null)
     */
    public static ValidationResult success() {
        return new ValidationResult(true, null);
    }
    
    /**
     * Creates a failing ValidationResult with a specific reason.
     *
     * @param reason the explanation for the validation failure
     * @return a failing ValidationResult (valid=false, reason=given reason)
     */
    public static ValidationResult failure(String reason) {
        return new ValidationResult(false, reason);
    }
}
