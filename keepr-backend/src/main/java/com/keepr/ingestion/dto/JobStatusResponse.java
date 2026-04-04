package com.keepr.ingestion.dto;

import com.keepr.ingestion.model.JobStatus;
import java.util.UUID;

/**
 * Response DTO for checking job status.
 *
 * @param jobId        the job UUID
 * @param status       the current lifecycle status
 * @param errorMessage error details if the job failed
 */
public record JobStatusResponse(
        UUID jobId,
        JobStatus status,
        String errorMessage
) {
}
