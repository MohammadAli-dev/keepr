package com.keepr.review.service.impl;

import com.keepr.common.exception.ErrorCode;
import com.keepr.common.exception.KeeprException;
import com.keepr.device.service.DeviceService;
import com.keepr.ingestion.model.ExtractionJob;
import com.keepr.ingestion.model.JobStatus;
import com.keepr.ingestion.repository.ExtractionJobRepository;
import com.keepr.review.dto.ConfirmReviewRequest;
import com.keepr.review.dto.ReviewTaskResponse;
import com.keepr.review.dto.ReviewTaskSummary;
import com.keepr.review.model.ReviewTask;
import com.keepr.review.model.ReviewTaskStatus;
import com.keepr.review.repository.ReviewTaskRepository;
import com.keepr.review.service.ReviewService;
import com.keepr.warranty.service.WarrantyService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewTaskRepository reviewTaskRepository;
    private final DeviceService deviceService;
    private final WarrantyService warrantyService;
    private final ExtractionJobRepository extractionJobRepository;

    /**
     * Create and persist a new review task for the specified extraction job and household.
     *
     * The created task is initialized with status PENDING and contains the provided raw text and extraction data.
     *
     * @param jobId         the extraction job UUID this review task belongs to
     * @param householdId   the household UUID that owns the review task
     * @param rawText       the original extracted text to be reviewed
     * @param extractionJson parsed extraction results associated with the task
     * @return              the saved ReviewTask instance
     */
    @Override
    public ReviewTask createReviewTask(UUID jobId, UUID householdId, String rawText, Map<String, Object> extractionJson) {
        // Fast-path pre-check (non-blocking)
        var existing = reviewTaskRepository.findByHouseholdIdAndJobId(householdId, jobId);
        if (existing.isPresent()) {
            log.info("[REVIEW_EXISTS_FAST] jobId={} householdId={}", jobId, householdId);
            return existing.get();
        }

        ReviewTask task = new ReviewTask();
        task.setJobId(jobId);
        task.setHouseholdId(householdId);
        task.setRawText(rawText);
        task.setExtractionJson(extractionJson);
        task.setStatus(ReviewTaskStatus.PENDING);

        try {
            ReviewTask savedTask = reviewTaskRepository.save(task);
            log.info("[REVIEW_CREATED] jobId={} householdId={}", jobId, householdId);
            return savedTask;
        } catch (DataIntegrityViolationException e) {
            log.warn("[REVIEW_EXISTS_RACE] Duplicate review task detected via DB constraint for jobId={}, returning existing", jobId);
            return reviewTaskRepository.findByHouseholdIdAndJobId(householdId, jobId)
                    .orElseThrow(() -> new KeeprException(ErrorCode.INTERNAL_ERROR, "Failed to fetch duplicate review task"));
        }
    }

    /**
     * Retrieve pending review tasks for the specified household, ordered by creation time descending.
     *
     * @param householdId the household UUID whose pending review tasks should be returned
     * @return a list of {@code ReviewTaskSummary} for tasks with status {@code PENDING}, ordered by {@code createdAt} descending
     */
    @Override
    public List<ReviewTaskSummary> getPendingTasks(UUID householdId) {
        return reviewTaskRepository.findByHouseholdIdAndStatusOrderByCreatedAtDesc(householdId, ReviewTaskStatus.PENDING)
                .stream()
                .map(task -> new ReviewTaskSummary(
                        task.getId(),
                        task.getJobId(),
                        task.getStatus(),
                        task.getCreatedAt()
                ))
                .toList();
    }

    /**
     * Retrieve a review task belonging to the specified household and return its detailed response.
     *
     * @param taskId      the UUID of the review task to retrieve
     * @param householdId the UUID of the household that must own the review task
     * @return            a ReviewTaskResponse containing the task's id, jobId, rawText, extractionJson, status, and createdAt
     * @throws KeeprException if no review task with the given id exists for the household (ErrorCode.NOT_FOUND)
     */
    @Override
    public ReviewTaskResponse getTask(UUID taskId, UUID householdId) {
        ReviewTask task = reviewTaskRepository.findByIdAndHouseholdId(taskId, householdId)
                .orElseThrow(() -> new KeeprException(ErrorCode.NOT_FOUND, "Review task not found"));

        return new ReviewTaskResponse(
                task.getId(),
                task.getJobId(),
                task.getRawText(),
                task.getExtractionJson(),
                task.getStatus(),
                task.getCreatedAt()
        );
    }

    /**
     * Confirms a review task: creates the device ingestion (and optional warranty), marks the task completed,
     * and updates the associated extraction job to USER_CONFIRMED.
     *
     * @param taskId      the review task identifier
     * @param householdId the household identifier owning the task
     * @param request     confirmation details; must include a non-blank device name, may include warranty info
     * @throws KeeprException if the review task or extraction job is not found, if the task is already completed,
     *                        or if the request is missing required device information (device name must not be blank)
     */
    @Override
    @Transactional
    public void confirmTask(UUID taskId, UUID householdId, ConfirmReviewRequest request) {
        ReviewTask task = reviewTaskRepository.findByIdAndHouseholdId(taskId, householdId)
                .orElseThrow(() -> new KeeprException(ErrorCode.NOT_FOUND, "Review task not found"));

        if (task.getStatus() != ReviewTaskStatus.PENDING) {
            throw new KeeprException(ErrorCode.CONFLICT, "Task already processed");
        }

        if (request.device() == null || request.device().name() == null || request.device().name().isBlank()) {
            throw new KeeprException(ErrorCode.BAD_REQUEST, "Device information is required and name must not be blank");
        }

        var deviceResponse = deviceService.createDeviceIngestion(request.device(), householdId);

        if (request.warranty() != null) {
            var originalWarranty = request.warranty();
            var newWarrantyRequest = new com.keepr.warranty.dto.CreateWarrantyRequest(
                    deviceResponse.deviceId(),
                    originalWarranty.type(),
                    originalWarranty.startDate(),
                    originalWarranty.endDate()
            );
            warrantyService.createWarrantyInternal(newWarrantyRequest, householdId);
        }

        task.setStatus(ReviewTaskStatus.COMPLETED);
        reviewTaskRepository.save(task);

        ExtractionJob job = extractionJobRepository.findByIdAndHouseholdId(java.util.Objects.requireNonNull(task.getJobId()), householdId)
                .orElseThrow(() -> new KeeprException(ErrorCode.NOT_FOUND, "Extraction job not found"));
        job.setStatus(JobStatus.USER_CONFIRMED);
        extractionJobRepository.save(job);

        log.info("[REVIEW_CONFIRMED] taskId={} householdId={}", taskId, householdId);
    }
}
