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

    /**
     * Transition the specified extraction job to PROCESSING and persist the update.
     *
     * @param jobId the UUID of the extraction job to mark as processing
     * @return the updated {@link ExtractionJob} after persisting the status and timestamp
     * @throws KeeprException with {@link ErrorCode#NOT_FOUND} if no job exists for the given id
     * @throws KeeprException with {@link ErrorCode#CONFLICT} if the job's current status is not PENDING or PROCESSING
     */
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

    /**
     * Marks the extraction job as requiring human review, persists extraction and confidence details, and creates a review task.
     *
     * <p>Updates the job's status to REVIEW_REQUIRED, stores raw text, extraction JSON, confidence metrics, timing and field counts,
     * and then enqueues a review task via the review service.</p>
     *
     * @param jobId the identifier of the extraction job to update
     * @param result the parsed extraction result to persist as extraction JSON
     * @param confResult the confidence calculation result used to populate score, breakdown, and field counts
     * @param rawText the OCR-extracted text associated with the job
     * @param ocrMs elapsed time spent in OCR in milliseconds
     * @param parseMs elapsed time spent in parsing in milliseconds
     * @param validateMs elapsed time spent in validation in milliseconds
     * @param failureReason a short code or message indicating why the job was routed to review (e.g., "LOW_CONFIDENCE" or "INVALID_DEVICE")
     * @throws KeeprException if the job with the given id does not exist
     */
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

    /**
     * Persist successful extraction results, create the associated device (and warranty when applicable), and mark the job completed.
     *
     * @param jobId the id of the extraction job to finalize
     * @param result the parsed extraction result to persist and map into device/warranty requests
     * @param confResult confidence metrics used to populate job confidence fields and field counts
     * @param rawText the OCR-extracted text to store on the job
     * @param ocrMs elapsed OCR time in milliseconds
     * @param parseMs elapsed parsing time in milliseconds
     * @param validateMs elapsed validation time in milliseconds
     * @param warrantyVal validation result for the warranty; when non-null and valid (and the extraction includes a warranty end date), a warranty will be created
     * @throws KeeprException if the job identified by {@code jobId} does not exist
     */
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

    /**
     * Builds a CreateDeviceRequest from a parsing extraction result.
     *
     * @param result the extraction result containing productName, brand, model, category, and purchaseDate
     * @return a CreateDeviceRequest populated with productName, brand, model, category (uses DEFAULT_CATEGORY when the extraction category is null), and purchaseDate
     */
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
