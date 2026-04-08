package com.keepr.review.dto;

import com.keepr.review.model.ReviewTaskStatus;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DTO representing the full details of a review task.
 *
 * @param id             the unique identifier of the review task
 * @param jobId          the identifier of the original extraction job
 * @param rawText        the raw OCR text snapshot
 * @param extractionJson the structured data snapshot to be corrected
 * @param status         the current status of the review task
 * @param createdAt      the timestamp when the review task was generated
 */
public record ReviewTaskResponse(
        UUID id,
        UUID jobId,
        String rawText,
        Map<String, Object> extractionJson,
        ReviewTaskStatus status,
        OffsetDateTime createdAt
) {}
