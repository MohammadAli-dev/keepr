package com.keepr.warranty.dto;

import java.time.LocalDate;
import java.util.UUID;
import com.keepr.warranty.model.WarrantyType;

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
        WarrantyType type,
        LocalDate startDate,
        LocalDate endDate) {
}
