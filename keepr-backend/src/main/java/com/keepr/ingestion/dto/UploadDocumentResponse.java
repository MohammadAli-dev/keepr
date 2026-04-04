package com.keepr.ingestion.dto;

import com.keepr.ingestion.model.JobStatus;
import java.util.UUID;

/**
 * Response DTO for a successful document upload.
 *
 * @param documentId the raw document UUID
 * @param jobId      the tracking job UUID
 * @param status     the initial job status
 */
public record UploadDocumentResponse(
        UUID documentId,
        UUID jobId,
        JobStatus status
) {
}
