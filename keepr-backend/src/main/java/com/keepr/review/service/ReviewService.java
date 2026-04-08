package com.keepr.review.service;

import com.keepr.review.dto.ConfirmReviewRequest;
import com.keepr.review.dto.ReviewTaskResponse;
import com.keepr.review.dto.ReviewTaskSummary;
import com.keepr.review.model.ReviewTask;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ReviewService {

    /**
     * Called by pipeline when confidence is below threshold or validation fails.
     */
    ReviewTask createReviewTask(UUID jobId, UUID householdId, String rawText, Map<String, Object> extractionJson);

    /**
     * Returns all PENDING review tasks for the household.
     */
    List<ReviewTaskSummary> getPendingTasks(UUID householdId);

    /**
     * Returns full detail of one task — 404 if not in household.
     */
    ReviewTaskResponse getTask(UUID taskId, UUID householdId);

    /**
     * User submits corrected data.
     */
    void confirmTask(UUID taskId, UUID householdId, ConfirmReviewRequest request);
}
