package com.keepr.ingestion.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
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
import com.keepr.review.service.ReviewService;
import com.keepr.warranty.dto.CreateWarrantyRequest;
import com.keepr.warranty.service.WarrantyService;
import com.keepr.ingestion.exception.ExtractionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
    private final ReviewService reviewService;


    @Value("${keepr.extraction.review-confidence-threshold:0.5}")
    private double reviewConfidenceThreshold;

    private static final String DEFAULT_CATEGORY = "OTHER";
    private static final String DEFAULT_WARRANTY_TYPE = "MANUFACTURER";

    /**
     * Orchestrates the processing of a single extraction job.
     */
    public void processJob(UUID jobId) {
        long totalStartTime = System.currentTimeMillis();
        String status = "SUCCESS";
        
        double confidence = 0.0;
        long ocrMs = 0;
        long parseMs = 0;
        long validateMs = 0;
        ConfidenceService.ConfidenceResult confResult = null;
        ParsingService.ExtractionResult parsingResult = null;
        ValidationResult warrantyVal = null;
        String rawText = null;

        log.info("Processing job id={}, version=1", jobId);
        
        try {
            ExtractionJob job = markProcessing(jobId);

            RawDocument doc = rawDocumentRepository.findByIdAndHouseholdId(
                    Objects.requireNonNull(job.getRawDocumentId()), 
                    Objects.requireNonNull(job.getHouseholdId()))
                    .orElseThrow(() -> new KeeprException(ErrorCode.NOT_FOUND, "Document not found"));

            long ocrStart = System.currentTimeMillis();
            try {
                rawText = ocrService.extractText(doc.getFileUrl());
            } finally {
                ocrMs = System.currentTimeMillis() - ocrStart;
            }

            long parseStart = System.currentTimeMillis();
            try {
                parsingResult = parsingService.parse(rawText);
                confResult = confidenceService.calculateConfidence(parsingResult);
                confidence = confResult.totalScore();
            } finally {
                parseMs = System.currentTimeMillis() - parseStart;
            }

            long valStart = System.currentTimeMillis();
            try {
                ValidationResult deviceVal = validationService.validateDevice(parsingResult, confidence);
                
                if (confidence < reviewConfidenceThreshold || !deviceVal.valid()) {
                    // Sprint 6: Route to Human Review
                    // Map validation failures to INVALID_DEVICE for legacy test compatibility
                    String reason = deviceVal.valid() ? "LOW_CONFIDENCE" : "INVALID_DEVICE";
                    markJobReviewRequired(jobId, parsingResult, confResult, rawText, 
                            (int) ocrMs, (int) parseMs, (int) validateMs, reason);
                    status = "REVIEW_REQUIRED";
                    return;
                }

                warrantyVal = validationService.validateWarranty(parsingResult);
            } finally {
                validateMs = System.currentTimeMillis() - valStart;
            }

            finalizeJob(jobId, parsingResult, confResult, rawText, 
                    (int) ocrMs, (int) parseMs, (int) validateMs, warrantyVal);

        } catch (ExtractionException e) {
            status = e.getFailureReason();
            log.error("Extraction validation failed: jobId={}, reason={}", jobId, status);
            ingestionFailureService.handleFailure(jobId, e, (int) ocrMs, (int) parseMs, (int) validateMs);
        } catch (Exception e) {
            status = "SYSTEM_ERROR";
            log.error("Job processing failed unexpectedly: jobId={}", jobId, e);
            ingestionFailureService.handleFailure(jobId, e, (int) ocrMs, (int) parseMs, (int) validateMs);
        } finally {
            long totalDuration = System.currentTimeMillis() - totalStartTime;
            log.info("[METRICS] jobId={} status={} totalMs={} ocrMs={} parseMs={}", 
                    jobId, status, totalDuration, ocrMs, parseMs);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExtractionJob markProcessing(UUID jobId) {
        ExtractionJob job = extractionJobRepository.findById(Objects.requireNonNull(jobId))
                .orElseThrow(() -> new KeeprException(ErrorCode.NOT_FOUND, "Job not found"));
        
        if (job.getStatus() != JobStatus.PENDING && job.getStatus() != JobStatus.PROCESSING) {
            throw new KeeprException(ErrorCode.CONFLICT, "Job is not in a processable state");
        }

        job.setStatus(JobStatus.PROCESSING);
        job.setUpdatedAt(OffsetDateTime.now());
        return extractionJobRepository.saveAndFlush(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markJobReviewRequired(UUID jobId, 
                                     ParsingService.ExtractionResult result, 
                                     ConfidenceService.ConfidenceResult confResult,
                                     String rawText,
                                     int ocrMs,
                                     int parseMs,
                                     int validateMs,
                                     String failureReason) {
        ExtractionJob job = extractionJobRepository.findById(Objects.requireNonNull(jobId))
                .orElseThrow(() -> new KeeprException(ErrorCode.NOT_FOUND, "Job not found"));

        job.setStatus(JobStatus.REVIEW_REQUIRED);
        job.setFailureReason(failureReason);
        job.setRawText(rawText);
        job.setConfidenceScore(confResult.totalScore());
        job.setConfidenceBreakdown(confResult.breakdown());
        job.setExtractionJson(parsingService.toMap(result));
        job.setExtractionVersion(1);
        job.setOcrMs(ocrMs);
        job.setParseMs(parseMs);
        job.setValidateMs(validateMs);
        job.setSuccessfulFields(confResult.successfulFields());
        job.setTotalFieldsExtracted(confResult.totalFields());
        job.setUpdatedAt(OffsetDateTime.now());
        
        extractionJobRepository.saveAndFlush(job);

        reviewService.createReviewTask(
                job.getId(),
                job.getHouseholdId(),
                rawText,
                job.getExtractionJson()
        );
        
        log.info("[REVIEW_CREATED] jobId={} confidence={} reason={}", 
                jobId, confResult.totalScore(), failureReason);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeJob(UUID jobId, 
                           ParsingService.ExtractionResult result, 
                           ConfidenceService.ConfidenceResult confResult,
                           String rawText,
                           int ocrMs,
                           int parseMs,
                           int validateMs,
                           ValidationResult warrantyVal) {
        ExtractionJob job = extractionJobRepository.findById(Objects.requireNonNull(jobId))
                .orElseThrow(() -> new KeeprException(ErrorCode.NOT_FOUND, "Job not found"));

        job.setRawText(rawText);
        job.setConfidenceScore(confResult.totalScore());
        job.setConfidenceBreakdown(confResult.breakdown());
        job.setExtractionJson(parsingService.toMap(result));
        job.setFailureReason(null);
        job.setExtractionVersion(1);
        
        job.setOcrMs(ocrMs);
        job.setParseMs(parseMs);
        job.setValidateMs(validateMs);
        job.setSuccessfulFields(confResult.successfulFields());
        job.setTotalFieldsExtracted(confResult.totalFields());

        DeviceResponse device = deviceService.createDeviceIngestion(
                toDeviceRequest(result), 
                job.getHouseholdId()
        );

        if (warrantyVal != null && warrantyVal.valid() && result.warrantyEnd() != null) {
            warrantyService.createWarrantyInternal(
                    toWarrantyRequest(result, device.deviceId()), 
                    job.getHouseholdId()
            );
        }

        job.setStatus(JobStatus.COMPLETED);
        job.setUpdatedAt(OffsetDateTime.now());
        extractionJobRepository.saveAndFlush(job);
        
        log.info("Job {} finalized successfully", jobId);
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
