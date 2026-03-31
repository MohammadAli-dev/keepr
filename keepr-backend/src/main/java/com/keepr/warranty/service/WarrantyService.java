package com.keepr.warranty.service;

import java.util.Set;

import com.keepr.common.exception.ErrorCode;
import com.keepr.common.exception.KeeprException;
import com.keepr.common.security.KeeprPrincipal;
import com.keepr.device.model.Device;
import com.keepr.device.repository.DeviceRepository;
import com.keepr.warranty.dto.CreateWarrantyRequest;
import com.keepr.warranty.dto.WarrantyResponse;
import com.keepr.warranty.model.Warranty;
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

    private static final Set<String> VALID_WARRANTY_TYPES = Set.of("MANUFACTURER", "EXTENDED", "AMC");

    private final WarrantyRepository warrantyRepository;
    private final DeviceRepository deviceRepository;

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
        validateCreateRequest(request);

        // Fetch device by ID AND householdId — returns 404 for both not-found and cross-tenant
        Device device = deviceRepository.findByIdAndHouseholdId(request.deviceId(), principal.householdId())
                .orElseThrow(() -> new KeeprException(
                        ErrorCode.NOT_FOUND,
                        "Device not found: " + request.deviceId()));

        Warranty warranty = new Warranty();
        warranty.setDeviceId(device.getId());
        warranty.setHouseholdId(principal.householdId());
        warranty.setType(request.type().toUpperCase());
        warranty.setStartDate(request.startDate());
        warranty.setEndDate(request.endDate());

        warranty = warrantyRepository.save(warranty);
        log.info("Warranty created: id={}, deviceId={}, householdId={}",
                warranty.getId(), warranty.getDeviceId(), warranty.getHouseholdId());

        return toResponse(warranty);
    }

    private void validateCreateRequest(CreateWarrantyRequest request) {
        if (request.deviceId() == null) {
            throw new KeeprException(ErrorCode.BAD_REQUEST, "Device ID is required");
        }
        if (request.type() == null || request.type().isBlank()) {
            throw new KeeprException(ErrorCode.BAD_REQUEST, "Warranty type is required");
        }
        if (!VALID_WARRANTY_TYPES.contains(request.type().toUpperCase())) {
            throw new KeeprException(ErrorCode.BAD_REQUEST,
                    "Warranty type must be one of: MANUFACTURER, EXTENDED, AMC");
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
