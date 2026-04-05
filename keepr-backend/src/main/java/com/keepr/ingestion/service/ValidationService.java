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
    public ValidationResult validateDevice(ParsingService.ExtractionResult result, double confidence) {
        log.debug("Validating device extraction: confidence={}, threshold={}", confidence, MIN_CONFIDENCE_THRESHOLD);
        if (result == null) {
            log.debug("Validation failed: ExtractionResult is null");
            return ValidationResult.failure("Missing mandatory parameter: result");
        }
        if (result.productName() == null || result.productName().isBlank()) {
            log.debug("Validation failed: Missing mandatory productName");
            return ValidationResult.failure("Missing mandatory field: productName");
        }
        if (confidence < MIN_CONFIDENCE_THRESHOLD) {
            log.debug("Validation failed: Low confidence ({} < {})", confidence, MIN_CONFIDENCE_THRESHOLD);
            return ValidationResult.failure("Low extraction confidence: " + confidence);
        }
        log.debug("Device validation successful");
        return ValidationResult.success();
    }

    /**
     * Validates the optional warranty portion of the extraction.
     * Logic: If dates are present, end date must not be before start date.
     */
    public ValidationResult validateWarranty(ParsingService.ExtractionResult result) {
        log.debug("Validating warranty extraction consistency");
        if (result == null) {
            log.debug("Warranty validation skipped: result is null");
            return ValidationResult.failure("Missing mandatory parameter: result");
        }
        if (result.warrantyStart() != null && result.warrantyEnd() != null) {
            if (result.warrantyEnd().isBefore(result.warrantyStart())) {
                log.debug("Warranty validation failed: end date {} is before start date {}", 
                        result.warrantyEnd(), result.warrantyStart());
                return ValidationResult.failure("Warranty end date cannot be before start date");
            }
        }
        // If one is missing but the other is present, we might still allow it as "partial" 
        // but for now we just check consistency if both exist.
        log.debug("Warranty validation successful/consistent");
        return ValidationResult.success();
    }
}
