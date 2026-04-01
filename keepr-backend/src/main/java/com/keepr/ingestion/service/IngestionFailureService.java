package com.keepr.ingestion.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.keepr.ingestion.model.ExtractionJob;
import com.keepr.ingestion.model.JobStatus;
import com.keepr.ingestion.model.RawDocument;
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
     * Handles job failure by incrementing retry count and updating status.
     * Runs in REQUIRES_NEW to ensure job state is persisted even if the main transaction rolls back.
     *
     * @param job those job that failed
     * @param e   the exception that caused the failure
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFailure(ExtractionJob job, Exception e) {
        log.error("Handling failure for jobId={}: {}", job.getId(), e.getMessage());
        
        job.setRetryCount(job.getRetryCount() + 1);
        job.setErrorMessage(e.getMessage());
        job.setUpdatedAt(OffsetDateTime.now());

        if (job.getRetryCount() >= MAX_RETRIES) {
            job.setStatus(JobStatus.FAILED);
            log.error("Job reached max retries and FAILED: jobId={}", job.getId());
            
            // Physical disk cleanup for permanent failures
            cleanupFile(job);
        } else {
            job.setStatus(JobStatus.PENDING);
            log.warn("Job marked for retry (count={}): jobId={}", job.getRetryCount(), job.getId());
        }
        
        extractionJobRepository.save(job);
    }

    private void cleanupFile(ExtractionJob job) {
        try {
            rawDocumentRepository.findByIdAndHouseholdId(job.getRawDocumentId(), job.getHouseholdId())
                    .ifPresent(doc -> {
                        log.info("Cleaning up physical file for failed job: jobId={}, path={}", job.getId(), doc.getFileUrl());
                        fileStorageService.delete(doc.getFileUrl());
                    });
        } catch (Exception ex) {
            log.error("Failed to cleanup file for jobId={}", job.getId(), ex);
        }
    }
}
