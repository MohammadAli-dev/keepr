package com.keepr.warranty.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Response body for warranty endpoints.
 *
 * @param warrantyId the warranty UUID
 * @param deviceId   the associated device UUID
 * @param type       the warranty type
 * @param startDate  the warranty start date
 * @param endDate    the warranty end date
 */
public record WarrantyResponse(
        UUID warrantyId,
        UUID deviceId,
        String type,
        LocalDate startDate,
        LocalDate endDate) {
}
