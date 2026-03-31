package com.keepr.warranty.dto;

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
        UUID deviceId,
        String type,
        LocalDate startDate,
        LocalDate endDate) {
}
