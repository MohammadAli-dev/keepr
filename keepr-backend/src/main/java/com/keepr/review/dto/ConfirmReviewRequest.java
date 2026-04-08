package com.keepr.review.dto;

import com.keepr.device.dto.CreateDeviceRequest;
import com.keepr.warranty.dto.CreateWarrantyRequest;

import jakarta.validation.Valid;

/**
 * DTO carrying device and warranty data for review confirmation.
 *
 * @param device   the corrected device information
 * @param warranty the optional corrected warranty information
 */
public record ConfirmReviewRequest(
        @Valid CreateDeviceRequest device,
        @Valid CreateWarrantyRequest warranty
) {}
