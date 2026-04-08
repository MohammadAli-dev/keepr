package com.keepr.review.dto;

import com.keepr.device.dto.CreateDeviceRequest;
import com.keepr.warranty.dto.CreateWarrantyRequest;

public record ConfirmReviewRequest(
        CreateDeviceRequest device,
        CreateWarrantyRequest warranty
) {}
