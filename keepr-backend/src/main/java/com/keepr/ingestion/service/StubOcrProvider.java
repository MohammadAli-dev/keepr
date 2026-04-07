package com.keepr.ingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Default OCR provider that returns a mock payload.
 * Used for development and testing.
 */
@Profile({"local", "test"})
@Component
@Slf4j
public class StubOcrProvider implements OcrProvider {

    private static final String MOCK_OCR_PAYLOAD = 
            "KEEP INVOICE\n" +
            "Device: MacBook Pro\n" +
            "Brand: Apple\n" +
            "Model: M3 Max\n" +
            "Purchase Date: 2024-01-01\n" +
            "Warranty Start: 2024-01-01\n" +
            "Warranty End: 2025-01-01\n" +
            "Warranty Type: MANUFACTURER\n" +
            "Category: LAPTOP\n" +
            "Serial: SN-123456-MBP-2024";

    @Override
    public String extractText(String fileUrl) {
        String safeUrl = fileUrl != null ? fileUrl.split("\\?")[0] : "unknown";
        log.info("Stub OCR provider extracting from: {}", safeUrl);
        return MOCK_OCR_PAYLOAD;
    }
}
