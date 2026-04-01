package com.keepr.ingestion.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.keepr.device.dto.DeviceResponse;
import com.keepr.device.service.DeviceService;
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

    private static final int MAX_RETRIES = 3;

    /**
     * Processes a single extraction job asynchronously.
     * Uses REQUIRES_NEW to ensure job state updates are committed even if domain logic fails,
     * while also allowing atomic rollback of domain side-effects upon exception.
     *
     * @param job the job to process
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processJob(ExtractionJob job) {
        log.info("Processing job: jobId={}, householdId={}", job.getId(), job.getHouseholdId());
        
        try {
            // 1. Transition state
            job.setStatus(JobStatus.PROCESSING);
            job.setUpdatedAt(OffsetDateTime.now());
            extractionJobRepository.saveAndFlush(job);

            // 2. Load RawDocument
            RawDocument doc = rawDocumentRepository.findById(job.getRawDocumentId())
                    .orElseThrow(() -> new RuntimeException("RawDocument not found: " + job.getRawDocumentId()));

            // 3. Extraction Flow (Stubs)
            String text = ocrService.extractText(doc.getFileUrl());
            ParsingService.ExtractionResult result = parsingService.parse(text);

            // 4. Persistence (Using internal service methods)
            DeviceResponse deviceRes = deviceService.createDeviceInternal(
                    result.getDeviceRequest(), 
                    job.getHouseholdId()
            );

            if (result.getWarrantyRequest() != null) {
                // Link warranty to the newly created/existing device
                CreateWarrantyRequest warrantyReq = updateWarrantyWithDeviceId(
                        result.getWarrantyRequest(), 
                        deviceRes.deviceId()
                );
                warrantyService.createWarrantyInternal(warrantyReq, job.getHouseholdId());
            }

            // 5. Finalize
            job.setStatus(JobStatus.COMPLETED);
            job.setErrorMessage(null);
            extractionJobRepository.save(job);
            log.info("Job completed successfully: jobId={}", job.getId());

        } catch (Exception e) {
            log.error("Job processing failed: jobId={}", job.getId(), e);
            handleFailure(job, e.getMessage());
        }
    }

    private void handleFailure(ExtractionJob job, String message) {
        job.setRetryCount(job.getRetryCount() + 1);
        job.setErrorMessage(message);
        job.setUpdatedAt(OffsetDateTime.now());

        if (job.getRetryCount() >= MAX_RETRIES) {
            job.setStatus(JobStatus.FAILED);
            log.error("Job reached max retries and FAILED: jobId={}", job.getId());
        } else {
            job.setStatus(JobStatus.PENDING);
            log.warn("Job marked for retry (count={}): jobId={}", job.getRetryCount(), job.getId());
        }
        
        extractionJobRepository.save(job);
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
