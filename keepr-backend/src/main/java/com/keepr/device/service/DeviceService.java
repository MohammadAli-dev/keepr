package com.keepr.device.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

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
import org.springframework.dao.DataIntegrityViolationException;
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
        device.setName(normalize(request.name()));
        device.setBrand(normalize(request.brand()));
        device.setModel(normalize(request.model()));
        device.setCategory(categoryEnum);
        device.setPurchaseDate(request.purchaseDate());

        device = deviceRepository.save(device);
        log.info("Device created manually: id={}, householdId={}", device.getId(), device.getHouseholdId());
        return toResponse(device);
    }

    /**
     * Specialized method for ingestion flows to prevent duplicate creation of logically
     * identical devices from the same extraction job.
     *
     * @param request     the device creation request
     * @param householdId the destination household UUID
     * @return the device response
     */
    public DeviceResponse createDeviceIngestion(CreateDeviceRequest request, UUID householdId) {
        DeviceCategory categoryEnum = validateAndParseCategory(request.category());
        validateCreateRequest(request);

        String name = normalize(request.name());
        String brand = normalize(request.brand());
        String model = normalize(request.model());

        // 1. First attempt: Standard idempotency check
        return deviceRepository.findByNameAndBrandAndModelAndHouseholdIdAndDeletedAtIsNull(
                name, brand, model, householdId)
                .map(device -> {
                    log.info("Returning existing device for idempotency: id={}", device.getId());
                    return toResponse(device);
                })
                .orElseGet(() -> {
                    try {
                        Device device = new Device();
                        device.setHouseholdId(householdId);
                        device.setName(name);
                        device.setBrand(brand);
                        device.setModel(model);
                        device.setCategory(categoryEnum);
                        device.setPurchaseDate(request.purchaseDate());

                        device = deviceRepository.save(device);
                        log.info("Device created: id={}, householdId={}", device.getId(), device.getHouseholdId());
                        return toResponse(device);
                    } catch (DataIntegrityViolationException e) {
                        log.warn("Duplicate device detected during concurrent save, re-fetching: " 
                                + "name={}, brand={}, model={}", name, brand, model);
                        // 2. Second attempt: Re-query using exactly the same normalized variables
                        return deviceRepository.findByNameAndBrandAndModelAndHouseholdIdAndDeletedAtIsNull(
                                name, brand, model, householdId)
                                .map(this::toResponse)
                                .orElseThrow(() -> new KeeprException(ErrorCode.INTERNAL_ERROR, 
                                        "Device creation failed after collision retry: " + name));
                    }
                });
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return null;
        }
        return v.toLowerCase(Locale.ROOT);
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
