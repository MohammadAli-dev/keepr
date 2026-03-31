package com.keepr.device.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Response body for device endpoints.
 *
 * @param deviceId     the device UUID
 * @param name         the device name
 * @param brand        the device brand
 * @param model        the device model
 * @param category     the device category
 * @param purchaseDate the purchase date
 */
public record DeviceResponse(
        UUID deviceId,
        String name,
        String brand,
        String model,
        String category,
        LocalDate purchaseDate) {
}
