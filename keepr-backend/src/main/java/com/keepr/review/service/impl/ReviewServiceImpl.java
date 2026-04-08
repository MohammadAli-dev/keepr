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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    public ReviewTask createReviewTask(UUID jobId, UUID householdId, String rawText, Map<String, Object> extractionJson) {
        ReviewTask task = new ReviewTask();
        task.setJobId(jobId);
        task.setHouseholdId(householdId);
        task.setRawText(rawText);
        task.setExtractionJson(extractionJson);
        task.setStatus(ReviewTaskStatus.PENDING);

        ReviewTask savedTask = reviewTaskRepository.save(task);
        log.info("[REVIEW_CREATED] jobId={} householdId={}", jobId, householdId);
        return savedTask;
    }

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

    @Override
    @Transactional
    public void confirmTask(UUID taskId, UUID householdId, ConfirmReviewRequest request) {
        ReviewTask task = reviewTaskRepository.findByIdAndHouseholdId(taskId, householdId)
                .orElseThrow(() -> new KeeprException(ErrorCode.NOT_FOUND, "Review task not found"));

        if (task.getStatus() == ReviewTaskStatus.COMPLETED) {
            throw new KeeprException(ErrorCode.BAD_REQUEST, "Review task already completed");
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

        ExtractionJob job = extractionJobRepository.findById(java.util.Objects.requireNonNull(task.getJobId()))
                .orElseThrow(() -> new KeeprException(ErrorCode.NOT_FOUND, "Extraction job not found"));
        job.setStatus(JobStatus.USER_CONFIRMED);
        job.setUpdatedAt(OffsetDateTime.now());
        extractionJobRepository.save(job);

        log.info("[REVIEW_CONFIRMED] taskId={} householdId={}", taskId, householdId);
    }
}
