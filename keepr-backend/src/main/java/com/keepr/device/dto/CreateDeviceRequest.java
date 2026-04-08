package com.keepr.device.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;

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
        @NotBlank(message = "Device name is required")
        String name,
        String brand,
        String model,
        @NotBlank(message = "Category is required")
        String category,
        @PastOrPresent(message = "Purchase date cannot be in the future")
        LocalDate purchaseDate) {
}
