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
 * Create a review task for the given job and household when extraction confidence is low or validation fails.
 *
 * @param jobId         identifier of the originating job
 * @param householdId   identifier of the household the task belongs to
 * @param rawText       original extracted text that requires review
 * @param extractionJson extracted data produced by the pipeline (raw JSON-like map)
 * @return              the created ReviewTask
 */
    ReviewTask createReviewTask(UUID jobId, UUID householdId, String rawText, Map<String, Object> extractionJson);

    /**
 * Retrieve review tasks for the household that are in the PENDING state.
 *
 * @param householdId the UUID of the household whose pending tasks should be returned
 * @return a list of ReviewTaskSummary objects representing tasks in the PENDING state; empty if none exist
 */
    List<ReviewTaskSummary> getPendingTasks(UUID householdId);

    /**
 * Retrieve detailed information for a specific review task scoped to a household.
 *
 * If the task is not associated with the provided household, a 404-level response is expected.
 *
 * @param taskId      the UUID of the review task to fetch
 * @param householdId the UUID of the household that must own the task
 * @return            the full details of the review task as a ReviewTaskResponse
 */
    ReviewTaskResponse getTask(UUID taskId, UUID householdId);

    /**
 * Record the user's confirmation or corrections for a specific review task.
 *
 * <p>The operation is scoped to the provided household and associates the submitted corrections
 * with the identified task.
 *
 * @param taskId the identifier of the review task to confirm
 * @param householdId the household identifier that scopes and authorizes the task
 * @param request the user's submitted corrections and related metadata
 */
    void confirmTask(UUID taskId, UUID householdId, ConfirmReviewRequest request);
}
