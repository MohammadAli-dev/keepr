package com.keepr.ingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Stub service simulating Optical Character Recognition.
 */
@Service
@Slf4j
public class OcrService {

    private final OcrProvider ocrProvider;

    public OcrService(OcrProvider ocrProvider) {
        this.ocrProvider = ocrProvider;
    }

    /**
     * Extracts text from a document by delegating to the configured provider.
     *
     * @param fileUrl path or URL to the document
     * @return the extracted raw text
     */
    public String extractText(String fileUrl) {
        log.info("Extracting document text via OCR provider...");
        return ocrProvider.extractText(fileUrl);
    }
}
