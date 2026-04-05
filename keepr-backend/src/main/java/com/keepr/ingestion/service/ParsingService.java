package com.keepr.ingestion.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for parsing raw OCR text into structured extraction results.
 * Uses regex and heuristic matching to identify key document fields.
 */
@Service
@Slf4j
public class ParsingService {

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
     * @return structured ExtractionResult containing parsed fields and segments
     */
    public ExtractionResult parse(String rawText) {
        log.info("Starting rule-based parsing of OCR text...");

        String productName = extract(rawText, "Device:\\s*(.*)");
        String brand = extract(rawText, "Brand:\\s*(.*)");
        String model = extract(rawText, "Model:\\s*(.*)");
        String category = extract(rawText, "Category:\\s*(.*)");
        String warrantyType = extract(rawText, "Warranty Type:\\s*(.*)");
        
        LocalDate purchaseDate = parseDate(extract(rawText, "Purchase Date:\\s*(\\d{4}-\\d{2}-\\d{2})"));
        LocalDate warrantyStart = parseDate(extract(rawText, "Warranty Start:\\s*(\\d{4}-\\d{2}-\\d{2})"));
        LocalDate warrantyEnd = parseDate(extract(rawText, "Warranty End:\\s*(\\d{4}-\\d{2}-\\d{2})"));

        ExtractionResult result = new ExtractionResult(
                productName, brand, model, category, 
                purchaseDate, warrantyStart, warrantyEnd, warrantyType
        );

        log.info("Parsing complete. Extracted: {}", productName);
        return result;
    }

    private String extract(String text, String regex) {
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
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
