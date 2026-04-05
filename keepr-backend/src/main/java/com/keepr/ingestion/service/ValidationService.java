package com.keepr.ingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for validating extraction results against business rules.
 */
@Service
@Slf4j
public class ValidationService {

    private static final double MIN_CONFIDENCE_THRESHOLD = 0.5;

    /**
     * Validates that the device portion of the extraction is sufficient.
     * Mandatory: productName must be present, and confidence must be above threshold.
     */
    public ValidationResult validateDevice(ParsingService.ExtractionResult result, double confidenceScore) {
        if (result == null) {
            return ValidationResult.failure("Missing mandatory parameter: result");
        }
        if (result.productName() == null || result.productName().isBlank()) {
            return ValidationResult.failure("Missing mandatory field: productName");
        }
        if (confidenceScore < MIN_CONFIDENCE_THRESHOLD) {
            return ValidationResult.failure("Low extraction confidence: " + confidenceScore);
        }
        return ValidationResult.success();
    }

    /**
     * Validates the optional warranty portion of the extraction.
     * Logic: If dates are present, end date must not be before start date.
     */
    public ValidationResult validateWarranty(ParsingService.ExtractionResult result) {
        if (result == null) {
            return ValidationResult.failure("Missing mandatory parameter: result");
        }
        if (result.warrantyStart() != null && result.warrantyEnd() != null) {
            if (result.warrantyEnd().isBefore(result.warrantyStart())) {
                return ValidationResult.failure("Warranty end date cannot be before start date");
            }
        }
        // If one is missing but the other is present, we might still allow it as "partial" 
        // but for now we just check consistency if both exist.
        return ValidationResult.success();
    }
}
