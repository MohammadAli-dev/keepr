package com.keepr.review.dto;

import com.keepr.review.model.ReviewTaskStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewTaskSummary(
        UUID id,
        UUID jobId,
        ReviewTaskStatus status,
        OffsetDateTime createdAt
) {}
