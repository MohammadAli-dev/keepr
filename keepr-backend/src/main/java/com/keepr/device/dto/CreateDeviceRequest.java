package com.keepr.device.dto;

import java.time.LocalDate;

/**
 * Request body for creating a new device.
 *
 * @param name         the device name (required)
 * @param brand        the device brand
 * @param model        the device model
 * @param category     the device category (required)
 * @param purchaseDate the purchase date (must not be in the future)
 */
public record CreateDeviceRequest(
        String name,
        String brand,
        String model,
        String category,
        LocalDate purchaseDate) {
}
