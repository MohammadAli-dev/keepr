package com.keepr.review.dto;

import com.keepr.review.model.ReviewTaskStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO representing a brief summary of a review task.
 *
 * @param id        the unique identifier of the review task
 * @param jobId     the identifier of the original extraction job
 * @param status    the current status of the review task
 * @param createdAt the timestamp when the review task was generated
 */
public record ReviewTaskSummary(
        UUID id,
        UUID jobId,
        ReviewTaskStatus status,
        OffsetDateTime createdAt
) {}
