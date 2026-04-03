package com.keepr.ingestion.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.keepr.ingestion.model.ExtractionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for ExtractionJob entity featuring the custom worker locking query.
 */
@Repository
public interface ExtractionJobRepository extends JpaRepository<ExtractionJob, UUID> {

    /**
     * Polls for pending jobs using FOR UPDATE SKIP LOCKED for high-concurrency worker safety.
     * Respects the retry backoff delay defined in the plan logic (internal calculation).
     *
     * @param limit maximum number of jobs to fetch per poll
     * @param now   the current time for backoff calculations
     * @return list of locked, pending jobs
     */
    @Query(value = """
            SELECT * FROM extraction_jobs 
            WHERE status = 'PENDING' 
            AND deleted_at IS NULL
            AND (
                (retry_count = 0) OR 
                (retry_count = 1 AND updated_at < :retry1Threshold) OR 
                (retry_count = 2 AND updated_at < :retry2Threshold)
            )
            ORDER BY created_at ASC 
            LIMIT :limit 
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ExtractionJob> findPendingJobsForUpdate(
            @Param("limit") int limit, 
            @Param("retry1Threshold") OffsetDateTime retry1Threshold,
            @Param("retry2Threshold") OffsetDateTime retry2Threshold);

    int MAX_RETRIES = 3;

    /**
     * Resets stale jobs stuck in PROCESSING to PENDING status to allow re-pickup.
     *
     * @param threshold the threshold time older than which a PROCESSING job is considered stuck
     * @param now       the current time to update timestamps
     * @return number of jobs reset
     */
    @Modifying
    @Query("UPDATE ExtractionJob j " +
            "SET j.status = CASE " +
            "  WHEN j.retryCount + 1 >= " + MAX_RETRIES + " THEN com.keepr.ingestion.model.JobStatus.FAILED " +
            "  ELSE com.keepr.ingestion.model.JobStatus.PENDING " +
            "END, " +
            "j.retryCount = j.retryCount + 1, " +
            "j.updatedAt = :now " +
            "WHERE j.status = com.keepr.ingestion.model.JobStatus.PROCESSING AND j.updatedAt < :threshold")
    int resetStaleJobs(@Param("threshold") OffsetDateTime threshold, @Param("now") OffsetDateTime now);

    /**
     * Fetch a job securely by its ID and household ID.
     *
     * @param id          the job UUID
     * @param householdId the household context
     * @return the extraction job if available
     */
    Optional<ExtractionJob> findByIdAndHouseholdId(UUID id, UUID householdId);
}
