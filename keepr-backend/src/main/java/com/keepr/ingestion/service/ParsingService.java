package com.keepr.ingestion.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.keepr.ingestion.exception.ExtractionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for parsing raw OCR text into structured extraction results.
 * Uses regex and heuristic matching to identify key document fields.
 */
@Service
@Slf4j
public class ParsingService {
    
    // Precompiled Pattern Constants to avoid magic strings
    private static final Pattern DEVICE_PATTERN = Pattern.compile("Device:\\s*(.*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BRAND_PATTERN = Pattern.compile("Brand:\\s*(.*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MODEL_PATTERN = Pattern.compile("Model:\\s*(.*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("Category:\\s*(.*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WARRANTY_TYPE_PATTERN = Pattern.compile("Warranty Type:\\s*(.*)", Pattern.CASE_INSENSITIVE);
    
    private static final Pattern PURCHASE_DATE_PATTERN = Pattern.compile("Purchase Date:\\s*(\\d{4}-\\d{2}-\\d{2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern WARRANTY_START_PATTERN = Pattern.compile("Warranty Start:\\s*(\\d{4}-\\d{2}-\\d{2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern WARRANTY_END_PATTERN = Pattern.compile("Warranty End:\\s*(\\d{4}-\\d{2}-\\d{2})", Pattern.CASE_INSENSITIVE);

    /**
     * Pure domain record for extraction results, decoupled from API DTOs.
     */
    public record ExtractionResult(
            String productName,
            String brand,
            String model,
            String category,
            LocalDate purchaseDate,
            LocalDate warrantyStart,
            LocalDate warrantyEnd,
            String warrantyType
    ) {}

    /**
     * Parses OCR text into a structured ExtractionResult.
     *
     * @param rawText the raw text from OCR
     * @return structured ExtractionResult containing parsed fields
     */
    public ExtractionResult parse(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            throw new ExtractionException("EMPTY_OCR_TEXT", "OCR returned empty or null text");
        }
        
        log.info("Starting rule-based parsing of OCR text...");

        String productName = extract(rawText, DEVICE_PATTERN);
        String brand = extract(rawText, BRAND_PATTERN);
        String model = extract(rawText, MODEL_PATTERN);
        String category = extract(rawText, CATEGORY_PATTERN);
        String warrantyType = extract(rawText, WARRANTY_TYPE_PATTERN);
        
        LocalDate purchaseDate = parseDate(extract(rawText, PURCHASE_DATE_PATTERN));
        LocalDate warrantyStart = parseDate(extract(rawText, WARRANTY_START_PATTERN));
        LocalDate warrantyEnd = parseDate(extract(rawText, WARRANTY_END_PATTERN));

        ExtractionResult result = new ExtractionResult(
                productName, brand, model, category, 
                purchaseDate, warrantyStart, warrantyEnd, warrantyType
        );

        log.info("Parsing complete. Extracted: {}", productName);
        return result;
    }

    private String extract(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (java.time.format.DateTimeParseException e) {
            log.warn("Failed to parse date: {}", dateStr, e);
            return null;
        }
    }

    /**
     * Converts an ExtractionResult into a Map for JSON serialization.
     *
     * @param result the result to convert
     * @return a map of extraction fields
     */
    public Map<String, Object> toMap(ExtractionResult result) {
        Map<String, Object> map = new HashMap<>();
        map.put("productName", result.productName());
        map.put("brand", result.brand());
        map.put("model", result.model());
        map.put("category", result.category());
        map.put("purchaseDate", result.purchaseDate());
        map.put("warrantyStart", result.warrantyStart());
        map.put("warrantyEnd", result.warrantyEnd());
        map.put("warrantyType", result.warrantyType());
        return map;
    }
}
