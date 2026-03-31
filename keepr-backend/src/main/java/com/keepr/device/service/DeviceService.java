package com.keepr.device.service;

import java.time.LocalDate;
import java.util.List;

import com.keepr.common.exception.ErrorCode;
import com.keepr.common.exception.KeeprException;
import com.keepr.common.security.KeeprPrincipal;
import com.keepr.device.dto.CreateDeviceRequest;
import com.keepr.device.dto.DeviceResponse;
import com.keepr.device.model.Device;
import com.keepr.device.model.DeviceCategory;
import com.keepr.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for device management operations.
 * All operations are strictly scoped to the authenticated user's household.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;

    /**
     * Creates a new device within the authenticated user's household.
     *
     * @param request   the device creation request
     * @param principal the authenticated user's principal
     * @return the created device response
     * @throws KeeprException if validation fails
     */
    public DeviceResponse createDevice(CreateDeviceRequest request, KeeprPrincipal principal) {
        DeviceCategory categoryEnum = validateAndParseCategory(request.category());
        validateCreateRequest(request);

        Device device = new Device();
        device.setHouseholdId(principal.householdId());
        device.setName(request.name().trim());
        device.setBrand(request.brand());
        device.setModel(request.model());
        device.setCategory(categoryEnum);
        device.setPurchaseDate(request.purchaseDate());

        device = deviceRepository.save(device);
        log.info("Device created: id={}, householdId={}", device.getId(), device.getHouseholdId());

        return toResponse(device);
    }

    /**
     * Lists all devices belonging to the authenticated user's household.
     *
     * @param principal the authenticated user's principal
     * @return list of device responses ordered by creation date descending
     */
    public List<DeviceResponse> listDevices(KeeprPrincipal principal) {
        return deviceRepository.findAllByHouseholdIdAndDeletedAtIsNullOrderByCreatedAtDesc(principal.householdId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void validateCreateRequest(CreateDeviceRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new KeeprException(ErrorCode.BAD_REQUEST, "Device name is required");
        }
        if (request.purchaseDate() != null && request.purchaseDate().isAfter(LocalDate.now())) {
            throw new KeeprException(ErrorCode.BAD_REQUEST, "Purchase date cannot be in the future");
        }
    }

    private DeviceResponse toResponse(Device device) {
        return new DeviceResponse(
                device.getId(),
                device.getName(),
                device.getBrand(),
                device.getModel(),
                device.getCategory(),
                device.getPurchaseDate()
        );
    }

    private DeviceCategory validateAndParseCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new KeeprException(ErrorCode.BAD_REQUEST, "Device category is required");
        }
        try {
            return DeviceCategory.valueOf(category.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new KeeprException(ErrorCode.BAD_REQUEST, "Invalid Category");
        }
    }
}
