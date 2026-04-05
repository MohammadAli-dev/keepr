package com.keepr.ingestion.service;

/**
 * Interface for OCR (Optical Character Recognition) providers.
 * Decouples the primary ingestion flow from specific OCR implementations.
 */
public interface OcrProvider {
    
    /**
     * Extracts text from the document at the specified URL.
     *
     * @param fileUrl path or URL to the document
     * @return the extracted raw text
     */
    String extractText(String fileUrl);
}
