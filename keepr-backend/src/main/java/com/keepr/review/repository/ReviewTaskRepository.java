package com.keepr.review.repository;

import com.keepr.review.model.ReviewTask;
import com.keepr.review.model.ReviewTaskStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewTaskRepository extends JpaRepository<ReviewTask, UUID> {

    List<ReviewTask> findByHouseholdIdAndStatusOrderByCreatedAtDesc(
            UUID householdId, ReviewTaskStatus status);

    Optional<ReviewTask> findByIdAndHouseholdId(UUID id, UUID householdId);

    Optional<ReviewTask> findByJobId(UUID jobId);

    @Deprecated
    @Override
    @NonNull
    Optional<ReviewTask> findById(@NonNull UUID id);
}
