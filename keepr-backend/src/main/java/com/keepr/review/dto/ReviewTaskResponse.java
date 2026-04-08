package com.keepr.review.dto;

import com.keepr.review.model.ReviewTaskStatus;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ReviewTaskResponse(
        UUID id,
        UUID jobId,
        String rawText,
        Map<String, Object> extractionJson,
        ReviewTaskStatus status,
        OffsetDateTime createdAt
) {}
