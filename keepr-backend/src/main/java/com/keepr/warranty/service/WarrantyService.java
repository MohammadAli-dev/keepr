package com.keepr.warranty.service;

import java.util.List;
import java.util.UUID;

import com.keepr.common.exception.ErrorCode;
import com.keepr.common.exception.KeeprException;
import com.keepr.common.security.KeeprPrincipal;
import com.keepr.device.model.Device;
import com.keepr.device.service.DeviceOwnershipService;
import com.keepr.warranty.dto.CreateWarrantyRequest;
import com.keepr.warranty.dto.WarrantyResponse;
import com.keepr.warranty.model.Warranty;
import com.keepr.warranty.model.WarrantyType;
import com.keepr.warranty.repository.WarrantyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for warranty management operations.
 * All operations are strictly scoped to the authenticated user's household.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WarrantyService {

    private final WarrantyRepository warrantyRepository;
    private final DeviceOwnershipService deviceOwnershipService;

    /**
     * Creates a new warranty attached to a device within the authenticated user's household.
     * The device must exist and belong to the same household.
     *
     * @param request   the warranty creation request
     * @param principal the authenticated user's principal
     * @return the created warranty response
     * @throws KeeprException if validation fails or the device is not found
     */
    public WarrantyResponse createWarranty(CreateWarrantyRequest request, KeeprPrincipal principal) {
        return createWarrantyInternal(request, principal.householdId());
    }

    /**
     * Internal method to create a warranty, used by both the API and background workers.
     * Implements idempotency to prevent duplicate creation of identical warranties.
     *
     * @param request     the warranty creation request
     * @param householdId the destination household UUID
     * @return the warranty response
     */
    public WarrantyResponse createWarrantyInternal(CreateWarrantyRequest request, UUID householdId) {
        WarrantyType typeEnum = validateAndParseType(request.type());
        validateDates(request);

        // Fetch device via ownership service — returns 404 for both not-found and cross-tenant
        Device device = deviceOwnershipService.getOwnedDevice(request.deviceId(), householdId);

        // Overlap validation and Idempotency check
        List<Warranty> existingWarranties = warrantyRepository.findAllByDeviceIdAndHouseholdIdAndDeletedAtIsNull(
                device.getId(), householdId);

        for (Warranty existing : existingWarranties) {
            if (existing.getType() == typeEnum) {
                if (existing.getStartDate() != null && existing.getEndDate() != null &&
                        request.startDate() != null && request.endDate() != null) {

                    // Exact match check for idempotency
                    if (existing.getStartDate().equals(request.startDate()) &&
                            existing.getEndDate().equals(request.endDate())) {
                        log.info("Returning existing warranty for idempotency: id={}", existing.getId());
                        return toResponse(existing);
                    }

                    // Overlap check
                    if (!request.startDate().isAfter(existing.getEndDate()) &&
                            !existing.getStartDate().isAfter(request.endDate())) {
                        throw new KeeprException(
                                ErrorCode.BAD_REQUEST,
                                "Overlapping warranty exists for this device and type");
                    }
                }
            }
        }

        Warranty warranty = new Warranty();
        warranty.setDeviceId(device.getId());
        warranty.setHouseholdId(householdId);
        warranty.setType(typeEnum);
        warranty.setStartDate(request.startDate());
        warranty.setEndDate(request.endDate());

        warranty = warrantyRepository.save(warranty);
        log.info("Warranty created: id={}, deviceId={}, householdId={}",
                warranty.getId(), warranty.getDeviceId(), warranty.getHouseholdId());

        return toResponse(warranty);
    }

    private void validateDates(CreateWarrantyRequest request) {
        if (request.deviceId() == null) {
            throw new KeeprException(ErrorCode.BAD_REQUEST, "Device ID is required");
        }
        if (request.startDate() == null) {
            throw new KeeprException(ErrorCode.BAD_REQUEST, "Start date is required");
        }
        if (request.endDate() == null) {
            throw new KeeprException(ErrorCode.BAD_REQUEST, "End date is required");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new KeeprException(ErrorCode.BAD_REQUEST, "End date must be on or after start date");
        }
    }

    private WarrantyType validateAndParseType(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) {
            throw new KeeprException(ErrorCode.BAD_REQUEST, "Warranty type is required");
        }
        try {
            return WarrantyType.valueOf(typeStr.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new KeeprException(ErrorCode.BAD_REQUEST,
                    "Warranty type must be one of: MANUFACTURER, EXTENDED, AMC");
        }
    }

    private WarrantyResponse toResponse(Warranty warranty) {
        return new WarrantyResponse(
                warranty.getId(),
                warranty.getDeviceId(),
                warranty.getType(),
                warranty.getStartDate(),
                warranty.getEndDate()
        );
    }
}
