package com.keepr.ingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Stub service simulating Optical Character Recognition.
 */
@Service
@Slf4j
public class OcrService {

    private static final String MOCK_OCR_PAYLOAD = 
            "MOCK INVOICE TEXT: Device: MacBook Pro, Brand: Apple, Model: M3 Max, " +
            "Warranty Start: 2024-01-01, Warranty End: 2025-01-01, Type: MANUFACTURER";

    /**
     * Simulates extracting text from a file.
     *
     * @param fileUrl path to the document
     * @return dummy extracted text
     */
    public String extractText(String fileUrl) {
        String safeUrl = fileUrl != null ? fileUrl.split("\\?")[0] : "unknown";
        log.info("Simulating OCR for file: {}", safeUrl);
        // In a real scenario, this would call AWS Textract or Google Vision
        return MOCK_OCR_PAYLOAD;
    }
}
