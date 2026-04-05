package com.keepr.ingestion.service;

/**
 * record for capturing the result of a validation check.
 * 
 * @param valid whether the validation passed
 * @param reason if invalid, the reason for failure
 */
public record ValidationResult(boolean valid, String reason) {
    
    public static ValidationResult success() {
        return new ValidationResult(true, null);
    }
    
    public static ValidationResult failure(String reason) {
        return new ValidationResult(false, reason);
    }
}
