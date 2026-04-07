package com.keepr.ingestion.service;

/**
 * Interface for OCR (Optical Character Recognition) providers.
 * Decouples the primary ingestion flow from specific OCR implementations.
 */
public interface OcrProvider {
    
    /**
     * Extracts text from the document at the specified URL.
     * The fileUrl must be a valid, accessible path (e.g., local file path, S3 URL, or HTTP URL).
     *
     * @param fileUrl path or URL to the document (must not be null or empty)
     * @return the extracted raw text (never null; returns an empty string if no text found)
     */
    String extractText(String fileUrl);
}
