package com.keepr.ingestion.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.keepr.ingestion.model.ExtractionJob;
import com.keepr.ingestion.model.JobStatus;
import com.keepr.ingestion.repository.ExtractionJobRepository;
import com.keepr.ingestion.repository.RawDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service dedicated to handling extraction job failures within an independent transaction.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionFailureService {

    private final ExtractionJobRepository extractionJobRepository;
    private final RawDocumentRepository rawDocumentRepository;
    private final FileStorageService fileStorageService;

    private static final int MAX_RETRIES = 3;

    /**
     * Handles processing failures by incrementing retry counts or marking as FAILED.
     * Runs in a separate transaction to ensure status updates are persisted even if 
     * the main processing transaction rolls back.
     *
     * @param jobId the ID of the job that failed
     * @param e     the exception that occurred
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFailure(UUID jobId, Exception e) {
        ExtractionJob job = extractionJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.error("Could not handle failure: Job not found: {}", jobId);
            return;
        }

        // Idempotency: skip if already terminal
        if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.FAILED) {
            log.info("Job {} already in terminal state {}, skipping failure handling",
                    jobId, job.getStatus());
            return;
        }

        int newRetryCount = job.getRetryCount() + 1;
        job.setRetryCount(newRetryCount);
        job.setErrorMessage(e.getMessage());
        job.setUpdatedAt(OffsetDateTime.now());

        if (newRetryCount >= MAX_RETRIES) {
            log.error("Job {} reached max retries (3). Marking as FAILED.", jobId);
            job.setStatus(JobStatus.FAILED);
            
            // Physical disk cleanup for permanent failures
            cleanupFile(job);
        } else {
            log.warn("Job {} failed (attempt {}). Marking as PENDING for retry.", jobId, newRetryCount);
            job.setStatus(JobStatus.PENDING);
            log.warn("Job marked for retry (count={}): jobId={}", job.getRetryCount(), job.getId());
        }
        
        extractionJobRepository.save(job);
    }

    private void cleanupFile(ExtractionJob job) {
        try {
            rawDocumentRepository.findByIdAndHouseholdId(job.getRawDocumentId(), job.getHouseholdId())
                    .ifPresent(doc -> {
                        log.info("Cleaning up physical file for failed job: jobId={}, path={}", 
                                job.getId(), doc.getFileUrl());
                        fileStorageService.delete(doc.getFileUrl());
                    });
        } catch (Exception ex) {
            log.error("Failed to cleanup file for jobId={}", job.getId(), ex);
        }
    }
}
