package com.keepr.ingestion.service;

import java.time.OffsetDateTime;
import java.util.List;

import com.keepr.ingestion.model.ExtractionJob;
import com.keepr.ingestion.repository.ExtractionJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Background worker that polls the ExtractionJobRepository for PENDING jobs.
 * Handles both the main processing loop and the zombie job recovery.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExtractionWorker {

    private final ExtractionJobRepository extractionJobRepository;
    private final IngestionProcessingService ingestionProcessingService;

    private static final int BATCH_SIZE = 5;
    private static final int STALE_THRESHOLD_MINUTES = 5;

    /**
     * Main background processing loop.
     * Polls for PENDING jobs every 5 seconds using FOR UPDATE SKIP LOCKED.
     */
    @Scheduled(fixedDelay = 5000)
    public void pollAndProcess() {
        log.info("Polling for extraction jobs...");
        
        List<ExtractionJob> jobs = extractionJobRepository.findPendingJobsForUpdate(
                BATCH_SIZE, 
                OffsetDateTime.now()
        );

        if (jobs.isEmpty()) {
            return;
        }

        log.info("Picked up {} jobs for processing", jobs.size());

        for (ExtractionJob job : jobs) {
            try {
                // Processing happens in its own REQUIRES_NEW transaction for isolation
                ingestionProcessingService.processJob(job);
            } catch (Exception e) {
                log.error("Failed to process extraction job ID={}: {}", job.getId(), e.getMessage(), e);
                // Isolation: do not re-throw, continue with the next job in the batch.
                // IngestionFailureService handled the persistent status update.
            }
        }
    }

    /**
     * Zombie Job Recovery.
     * Searches for jobs stuck in PROCESSING for more than 5 minutes and resets them to PENDING.
     * Runs every 60 seconds.
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void recoverStaleJobs() {
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES);
        int resetCount = extractionJobRepository.resetStaleJobs(threshold, OffsetDateTime.now());
        
        if (resetCount > 0) {
            log.warn("Recovered {} stale processing jobs", resetCount);
        }
    }
}
