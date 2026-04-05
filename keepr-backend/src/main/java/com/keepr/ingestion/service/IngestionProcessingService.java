package com.keepr.ingestion.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.keepr.device.dto.CreateDeviceRequest;
import com.keepr.device.dto.DeviceResponse;
import com.keepr.device.service.DeviceService;
import com.keepr.common.exception.ErrorCode;
import com.keepr.common.exception.KeeprException;
import com.keepr.ingestion.model.ExtractionJob;
import com.keepr.ingestion.model.JobStatus;
import com.keepr.ingestion.model.RawDocument;
import com.keepr.ingestion.repository.ExtractionJobRepository;
import com.keepr.ingestion.repository.RawDocumentRepository;
import com.keepr.warranty.dto.CreateWarrantyRequest;
import com.keepr.warranty.service.WarrantyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.keepr.ingestion.exception.ExtractionException;

/**
 * Service for the logic of processing individual extraction jobs.
 * Orchestrates OCR, parsing, validation, and final persistence.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionProcessingService {

    private final ExtractionJobRepository extractionJobRepository;
    private final RawDocumentRepository rawDocumentRepository;
    private final OcrService ocrService;
    private final ParsingService parsingService;
    private final ConfidenceService confidenceService;
    private final ValidationService validationService;
    private final DeviceService deviceService;
    private final WarrantyService warrantyService;
    private final IngestionFailureService ingestionFailureService;

    private static final String DEFAULT_CATEGORY = "OTHER";
    private static final String DEFAULT_WARRANTY_TYPE = "MANUFACTURER";

    /**
     * Orchestrates the processing of a single extraction job.
     * This method is NON-TRANSACTIONAL to release DB connections during
     * long-running OCR and parsing I/O.
     */
    public void processJob(UUID jobId) {
        long totalStartTime = System.currentTimeMillis();
        String status = "SUCCESS";
        
        // Metrics to capture
        double confidence = 0.0;
        long ocrMs = 0;
        long parseMs = 0;
        long validateMs = 0;
        ConfidenceService.ConfidenceResult confResult = null;
        ParsingService.ExtractionResult parsingResult = null;
        String rawText = null;

        log.info("Processing job id={}, version=1", jobId);
        
        try {
            // Phase 1: Mark as PROCESSING [REQUIRES_NEW]
            ExtractionJob job = markProcessing(jobId);

            // Phase 2: Extraction & Validation [NO TRANSACTION]
            RawDocument doc = rawDocumentRepository.findByIdAndHouseholdId(job.getRawDocumentId(), job.getHouseholdId())
                    .orElseThrow(() -> new KeeprException(ErrorCode.NOT_FOUND, "Document not found"));

            // 2.1 OCR Stage
            long ocrStart = System.currentTimeMillis();
            try {
                rawText = ocrService.extractText(doc.getFileUrl());
            } finally {
                ocrMs = System.currentTimeMillis() - ocrStart;
            }

            // 2.2 Parsing Stage
            long parseStart = System.currentTimeMillis();
            try {
                parsingResult = parsingService.parse(rawText);
                confResult = confidenceService.calculateConfidence(parsingResult);
                confidence = confResult.totalScore();
            } finally {
                parseMs = System.currentTimeMillis() - parseStart;
            }

            // 2.3 Validation Stage
            long valStart = System.currentTimeMillis();
            try {
                ValidationResult deviceVal = validationService.validateDevice(parsingResult, confidence);
                if (!deviceVal.valid()) {
                    log.warn("Device validation failed for job {}: {}", jobId, deviceVal.reason());
                    throw new ExtractionException("INVALID_DEVICE", deviceVal.reason());
                }

                // Optional Warranty Validation (Log but don't fail)
                ValidationResult warrantyVal = validationService.validateWarranty(parsingResult);
                if (!warrantyVal.valid()) {
                    log.info("Warranty validation for job {}: {}. Skipping warranty.",
                            jobId, warrantyVal.reason());
                }
            } finally {
                validateMs = System.currentTimeMillis() - valStart;
            }

            // Phase 3: Atomic Finalization [REQUIRES_NEW]
            finalizeJob(jobId, parsingResult, confResult, rawText, 
                    (int) ocrMs, (int) parseMs, (int) validateMs);

        } catch (ExtractionException e) {
            status = e.getFailureReason();
            log.error("Extraction validation failed: jobId={}, reason={}, message={}", jobId, status, e.getMessage());
            ingestionFailureService.handleFailure(jobId, e);
        } catch (Exception e) {
            status = "SYSTEM_ERROR";
            log.error("Job processing failed unexpectedly: jobId={}", jobId, e);
            ingestionFailureService.handleFailure(jobId, e);
        } finally {
            long totalDuration = System.currentTimeMillis() - totalStartTime;
            log.info("[METRICS] jobId={} version=1 confidence={} status={} " 
                            + "totalMs={} ocrMs={} parseMs={} validateMs={}", 
                    jobId, confidence, status, totalDuration, ocrMs, parseMs, validateMs);
        }
    }

    /**
     * Transitions a job to PROCESSING status and commits immediately.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExtractionJob markProcessing(UUID jobId) {
        ExtractionJob job = extractionJobRepository.findById(jobId)
                .orElseThrow(() -> new KeeprException(ErrorCode.NOT_FOUND, "Job not found"));
        
        if (job.getStatus() == JobStatus.PROCESSING) {
            return job;
        }

        job.setStatus(JobStatus.PROCESSING);
        job.setUpdatedAt(OffsetDateTime.now());
        return extractionJobRepository.saveAndFlush(job);
    }

    /**
     * Atomic persistence of extraction results and job completion.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeJob(UUID jobId, 
                           ParsingService.ExtractionResult result, 
                           ConfidenceService.ConfidenceResult confResult,
                           String rawText,
                           int ocrMs,
                           int parseMs,
                           int validateMs) {
        ExtractionJob job = extractionJobRepository.findById(jobId)
                .orElseThrow(() -> new KeeprException(ErrorCode.NOT_FOUND, "Job not found"));

        // 1. Persist Job Metadata & Metrics
        job.setRawText(rawText);
        job.setConfidenceScore(confResult.totalScore());
        job.setConfidenceBreakdown(confResult.breakdown());
        job.setExtractionJson(parsingService.toMap(result));
        job.setFailureReason(null);
        job.setExtractionVersion(1); // Explicitly setting version
        
        // Timing Metrics
        job.setOcrMs(ocrMs);
        job.setParseMs(parseMs);
        job.setValidateMs(validateMs);
        
        // Success Metrics
        job.setSuccessfulFields(confResult.successfulFields());
        job.setTotalFieldsExtracted(confResult.totalFields());

        // 2. Map & Create Device (REQUIRED)
        DeviceResponse device = deviceService.createDeviceIngestion(
                toDeviceRequest(result), 
                job.getHouseholdId()
        );

        // 3. Optional Warranty Creation
        ValidationResult warrantyVal = validationService.validateWarranty(result);
        if (warrantyVal.valid() && result.warrantyEnd() != null) {
            warrantyService.createWarrantyInternal(
                    toWarrantyRequest(result, device.deviceId()), 
                    job.getHouseholdId()
            );
        }

        // 4. Mark Job Completed
        job.setStatus(JobStatus.COMPLETED);
        job.setErrorMessage(null);
        job.setUpdatedAt(OffsetDateTime.now());
        extractionJobRepository.saveAndFlush(job);
        
        log.info("Job {} finalized successfully (v1). Confidence: {}, ocrMs: {}, parseMs: {}", 
                jobId, confResult.totalScore(), ocrMs, parseMs);
    }

    private CreateDeviceRequest toDeviceRequest(ParsingService.ExtractionResult result) {
        return new CreateDeviceRequest(
                result.productName(),
                result.brand(),
                result.model(),
                result.category() != null ? result.category() : DEFAULT_CATEGORY,
                result.purchaseDate()
        );
    }

    private CreateWarrantyRequest toWarrantyRequest(ParsingService.ExtractionResult result, UUID deviceId) {
        return new CreateWarrantyRequest(
                deviceId,
                result.warrantyType() != null ? result.warrantyType() : DEFAULT_WARRANTY_TYPE,
                result.warrantyStart(),
                result.warrantyEnd()
        );
    }
}
