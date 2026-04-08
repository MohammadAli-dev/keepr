package com.keepr.review.model;

/**
 * Enum representing the lifecycle state of a review task.
 */
public enum ReviewTaskStatus {
    /** The task is newly created and awaiting human intervention. */
    PENDING,
    /** The task has been reviewed, corrected, and confirmed by a human. */
    COMPLETED
}
