package com.keepr.ingestion.service;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for calculating extraction confidence and providing granular breakdowns.
 * Decouples scoring logic from parsing for independent tuning and testing.
 */
@Service
@Slf4j
public class ConfidenceService {

    // Stable constants for breakdown keys (snake_case for database analytics)
    public static final String CONF_PRODUCT_NAME = "product_name";
    public static final String CONF_BRAND = "brand";
    public static final String CONF_MODEL = "model";
    public static final String CONF_CATEGORY = "category";
    public static final String CONF_PURCHASE_DATE = "purchase_date";
    public static final String CONF_WARRANTY_END = "warranty_end";

    /**
     * Data object for scoring results.
     */
    public record ConfidenceResult(
            double totalScore,
            Map<String, Double> breakdown,
            int successfulFields,
            int totalFields
    ) {}

    /**
     * Calculates the aggregate and field-level confidence for an extraction.
     *
     * @param result the parsed extraction result
     * @return a ConfidenceResult with total score and breakdown map
     */
    public ConfidenceResult calculateConfidence(ParsingService.ExtractionResult result) {
        Map<String, Double> breakdown = new HashMap<>();
        double totalScore = 0.0;

        // 1. Product Name (Weight: 0.3)
        double productNameScore = (result.productName() != null && !result.productName().isBlank()) ? 0.3 : 0.0;
        breakdown.put(CONF_PRODUCT_NAME, productNameScore);
        totalScore += productNameScore;

        // 2. Brand (Weight: 0.2)
        double brandScore = (result.brand() != null && !result.brand().isBlank()) ? 0.2 : 0.0;
        breakdown.put(CONF_BRAND, brandScore);
        totalScore += brandScore;

        // 3. Model (Weight: 0.1)
        double modelScore = (result.model() != null && !result.model().isBlank()) ? 0.1 : 0.0;
        breakdown.put(CONF_MODEL, modelScore);
        totalScore += modelScore;

        // 4. Category (Weight: 0.0 - currently informational)
        breakdown.put(CONF_CATEGORY, 0.0);

        // 5. Purchase Date (Weight: 0.2)
        double dateScore = (result.purchaseDate() != null) ? 0.2 : 0.0;
        breakdown.put(CONF_PURCHASE_DATE, dateScore);
        totalScore += dateScore;

        // 6. Warranty End (Weight: 0.2)
        double warrantyEndScore = (result.warrantyEnd() != null) ? 0.2 : 0.0;
        breakdown.put(CONF_WARRANTY_END, warrantyEndScore);
        totalScore += warrantyEndScore;

        // Calculate metrics based on the breakdown
        int successful = 0;
        for (Double score : breakdown.values()) {
            if (score > 0) {
                successful++;
            }
        }

        return new ConfidenceResult(
                Math.min(1.0, totalScore),
                breakdown,
                successful,
                breakdown.size()
        );
    }
}
