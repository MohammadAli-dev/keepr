package com.keepr.review.controller;

import com.keepr.common.security.KeeprPrincipal;
import com.keepr.review.dto.ConfirmReviewRequest;
import com.keepr.review.dto.ReviewTaskResponse;
import com.keepr.review.dto.ReviewTaskSummary;
import com.keepr.review.service.ReviewService;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/review")
@Slf4j
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * Gets all pending review tasks for the authenticated user's household.
     *
     * @param principal the authenticated user principal
     * @return a list of review task summaries
     */
    @GetMapping("/tasks")
    public ResponseEntity<List<ReviewTaskSummary>> getPendingTasks(
            @AuthenticationPrincipal KeeprPrincipal principal) {
        log.debug("Fetching pending review tasks for household: {}", principal.householdId());
        return ResponseEntity.ok(reviewService.getPendingTasks(principal.householdId()));
    }

    /**
     * Gets full detail of the requested review task.
     *
     * @param id        the review task ID
     * @param principal the authenticated user principal
     * @return the review task details
     */
    @GetMapping("/tasks/{id}")
    public ResponseEntity<ReviewTaskResponse> getTask(
            @PathVariable UUID id,
            @AuthenticationPrincipal KeeprPrincipal principal) {
        log.debug("Fetching review task {} for household: {}", id, principal.householdId());
        return ResponseEntity.ok(reviewService.getTask(id, principal.householdId()));
    }

    /**
     * Submits verified manual review task data.
     *
     * @param id        the review task ID
     * @param request   the corrected structured device and warranty data
     * @param principal the authenticated user principal
     * @return 200 OK
     */
    @PostMapping("/tasks/{id}/confirm")
    public ResponseEntity<Void> confirmTask(
            @PathVariable UUID id,
            @RequestBody @Valid ConfirmReviewRequest request,
            @AuthenticationPrincipal KeeprPrincipal principal) {
        log.info("Received review task confirmation request for task {} from household {}", id, principal.householdId());
        reviewService.confirmTask(id, principal.householdId(), request);
        return ResponseEntity.ok().build();
    }
}
