package com.keepr.ingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Stub service simulating Optical Character Recognition.
 */
@Service
@Slf4j
public class OcrService {

    /**
     * Simulates extracting text from a file.
     *
     * @param fileUrl path to the document
     * @return dummy extracted text
     */
    public String extractText(String fileUrl) {
        log.info("Simulating OCR for file: {}", fileUrl);
        // In a real scenario, this would call AWS Textract or Google Vision
        return "MOCK INVOICE TEXT: Device: MacBook Pro, Brand: Apple, Model: M3 Max, " +
               "Warranty Start: 2024-01-01, Warranty End: 2025-01-01, Type: MANUFACTURER";
    }
}
