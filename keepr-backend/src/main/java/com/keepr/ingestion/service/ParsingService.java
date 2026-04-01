package com.keepr.ingestion.service;

import java.time.LocalDate;

import com.keepr.device.dto.CreateDeviceRequest;
import com.keepr.warranty.dto.CreateWarrantyRequest;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Stub service simulating the parsing of raw OCR text into structured objects.
 */
@Service
@Slf4j
public class ParsingService {

    /**
     * Data object for holding extraction results.
     */
    @Value
    public static class ExtractionResult {
        CreateDeviceRequest deviceRequest;
        CreateWarrantyRequest warrantyRequest;
    }

    /**
     * Parses OCR text into stubbed device and warranty data.
     *
     * @param ocrText raw text extracted from OCR
     * @return structured ExtractionResult
     */
    public ExtractionResult parse(String ocrText) {
        log.info("Parsing OCR text...");

        // In a real scenario, this would use LLM or regex to find fields
        // For Sprint 4, we use a predictable stub.

        CreateDeviceRequest deviceRequest = new CreateDeviceRequest(
                "MacBook Pro",
                "Apple",
                "M3 Max",
                "LAPTOP",
                LocalDate.of(2024, 1, 1)
        );

        CreateWarrantyRequest warrantyRequest = new CreateWarrantyRequest(
                null, // Device ID will be filled later after device creation
                "MANUFACTURER",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2025, 1, 1)
        );

        return new ExtractionResult(deviceRequest, warrantyRequest);
    }
}
