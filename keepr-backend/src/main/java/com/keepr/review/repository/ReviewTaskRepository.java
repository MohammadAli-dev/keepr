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

    /**
             * Finds review tasks for the specified household with the given status, ordered by creation time descending.
             *
             * @param householdId the household UUID to filter tasks by
             * @param status the ReviewTaskStatus to filter tasks by
             * @return a list of matching ReviewTask entities ordered by createdAt in descending order; empty if none found
             */
            List<ReviewTask> findByHouseholdIdAndStatusOrderByCreatedAtDesc(
            UUID householdId, ReviewTaskStatus status);

    /**
 * Finds a review task by its id that belongs to the specified household.
 *
 * @param id the id of the review task
 * @param householdId the id of the household the review task must belong to
 * @return an Optional containing the matching ReviewTask if found, otherwise Optional.empty()
 */
Optional<ReviewTask> findByIdAndHouseholdId(UUID id, UUID householdId);

    /**
 * Finds a ReviewTask associated with the given job identifier.
 *
 * @param jobId the job UUID to match against a ReviewTask's jobId
 * @return an Optional containing the matching ReviewTask if found, or Optional.empty() if none exists
 */
Optional<ReviewTask> findByJobId(UUID jobId);

    /**
     * Locates a ReviewTask by its identifier.
     *
     * @param id the UUID of the ReviewTask; must not be null
     * @return an Optional containing the matching ReviewTask, or Optional.empty() if none is found
     * @deprecated Prefer scoped lookup methods (for example, those that constrain by household or job) to avoid ambiguous global lookups.
     */
    @Deprecated
    @Override
    @NonNull
    Optional<ReviewTask> findById(@NonNull UUID id);
}
