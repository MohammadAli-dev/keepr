package com.keepr.device.service;

import java.util.UUID;

import com.keepr.common.exception.ErrorCode;
import com.keepr.common.exception.KeeprException;
import com.keepr.device.model.Device;
import com.keepr.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Shared service for verifying cross-module device ownership and existence.
 * Extracts repository-level constraints (like tenant scoping and soft-delete filtering)
 * so other modules can look up devices securely.
 */
@Service
@RequiredArgsConstructor
public class DeviceOwnershipService {

    private final DeviceRepository deviceRepository;

    /**
     * Retrieves an active device belonging to the specified household.
     * Throws a structured KeeprException if the device does not exist,
     * is softly deleted, or belongs to a different household.
     *
     * @param deviceId    the device UUID
     * @param householdId the target household UUID
     * @return the valid Device entity
     * @throws KeeprException if not found or cross-tenant access attempted
     */
    public Device getOwnedDevice(UUID deviceId, UUID householdId) {
        return deviceRepository.findByIdAndHouseholdIdAndDeletedAtIsNull(deviceId, householdId)
                .orElseThrow(() -> new KeeprException(
                        ErrorCode.NOT_FOUND,
                        "Device not found or access forbidden: " + deviceId));
    }
}
