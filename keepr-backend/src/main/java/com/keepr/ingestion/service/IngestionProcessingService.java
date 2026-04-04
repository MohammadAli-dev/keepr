package com.keepr.ingestion.service;

import java.time.OffsetDateTime;
import java.util.UUID;

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

/**
 * Service for the logic of processing individual extraction jobs.
 * Runs each job in its own transaction (REQUIRES_NEW) for isolation and failure management.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionProcessingService {

    private final ExtractionJobRepository extractionJobRepository;
    private final RawDocumentRepository rawDocumentRepository;
    private final OcrService ocrService;
    private final ParsingService parsingService;
    private final DeviceService deviceService;
    private final WarrantyService warrantyService;
    private final IngestionFailureService ingestionFailureService;

    /**
     * Orchestrates the processing of a single extraction job.
     * This method is NON-TRANSACTIONAL to ensure DB connections are not held during
     * long-running OCR and parsing I/O.
     *
     * @param jobId the ID of the job to process
     */
    public void processJob(UUID jobId) {
        log.info("Orchestrating processing for job id={}", jobId);
        
        try {
            // Phase 1: Mark as PROCESSING in a dedicated transaction
            ExtractionJob job = markProcessing(jobId);

            // Phase 2: Heavy I/O (OCR + Parsing) - NO TRANSACTION
            RawDocument doc = rawDocumentRepository.findByIdAndHouseholdId(job.getRawDocumentId(), job.getHouseholdId())
                    .orElseThrow(() -> new KeeprException(ErrorCode.NOT_FOUND, 
                            "RawDocument not found for household: " + job.getRawDocumentId()));

            String text = ocrService.extractText(doc.getFileUrl());
            ParsingService.ExtractionResult result = parsingService.parse(text);

            // Phase 3: Finalize and persist domain changes in dedicated transaction
            finalizeJob(jobId, result);

        } catch (Exception e) {
            log.error("Job processing failed: jobId={}", jobId, e);
            ingestionFailureService.handleFailure(jobId, e);
        }
    }

    /**
     * Transitions a job to PROCESSING status and commits immediately.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExtractionJob markProcessing(UUID jobId) {
        ExtractionJob job = extractionJobRepository.findById(jobId)
                .orElseThrow(() -> new KeeprException(ErrorCode.NOT_FOUND, "Job not found: " + jobId));
        
        // Skip if already processing (safety for concurrent picking)
        if (job.getStatus() == JobStatus.PROCESSING) {
            return job;
        }

        job.setStatus(JobStatus.PROCESSING);
        job.setUpdatedAt(OffsetDateTime.now());
        return extractionJobRepository.saveAndFlush(job);
    }

    /**
     * Persists extracted domain entities and marks the job as COMPLETED.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeJob(UUID jobId, ParsingService.ExtractionResult result) {
        ExtractionJob job = extractionJobRepository.findById(jobId)
                .orElseThrow(() -> new KeeprException(ErrorCode.NOT_FOUND, "Job not found: " + jobId));

        // Create Device (using ingestion-specific idempotent method)
        DeviceResponse deviceRes = deviceService.createDeviceIngestion(
                result.deviceRequest(), 
                job.getHouseholdId()
        );

        if (result.warrantyRequest() != null) {
            // Link warranty to the newly created/existing device
            CreateWarrantyRequest warrantyReq = updateWarrantyWithDeviceId(
                    result.warrantyRequest(), 
                    deviceRes.deviceId()
            );
            warrantyService.createWarrantyInternal(warrantyReq, job.getHouseholdId());
        }

        // Finalize job record
        job.setStatus(JobStatus.COMPLETED);
        job.setErrorMessage(null);
        job.setUpdatedAt(OffsetDateTime.now());
        extractionJobRepository.saveAndFlush(job);
        
        log.info("Job {} completed successfully", jobId);
    }

    private CreateWarrantyRequest updateWarrantyWithDeviceId(CreateWarrantyRequest original, UUID deviceId) {
        return new CreateWarrantyRequest(
                deviceId,
                original.type(),
                original.startDate(),
                original.endDate()
        );
    }
}
