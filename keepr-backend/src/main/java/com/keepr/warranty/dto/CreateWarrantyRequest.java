package com.keepr.warranty.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for creating a new warranty.
 *
 * @param deviceId  the device UUID to attach the warranty to (required)
 * @param type      the warranty type: MANUFACTURER, EXTENDED, or AMC (required)
 * @param startDate the warranty start date (required)
 * @param endDate   the warranty end date (required, must be >= startDate)
 */
public record CreateWarrantyRequest(
        @NotNull(message = "Device ID is required")
        UUID deviceId,
        @NotBlank(message = "Warranty type is required")
        String type,
        @NotNull(message = "Start date is required")
        LocalDate startDate,
        @NotNull(message = "End date is required")
        LocalDate endDate) {

    /**
     * Ensures the end date is not before the start date.
     *
     * @return true if the date range is valid
     */
    @AssertTrue(message = "End date must be on or after start date")
    public boolean isValidDateRange() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
