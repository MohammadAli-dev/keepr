package com.keepr.device.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.keepr.device.model.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link Device} entities.
 * All queries are scoped by householdId for strict multi-tenancy.
 */
@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {

    /**
     * Finds all devices belonging to a household that are not deleted, ordered by creation date descending.
     *
     * @param householdId the household UUID
     * @return list of devices ordered newest-first
     */
    List<Device> findAllByHouseholdIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID householdId);

    /**
     * Finds a device by its ID and household ID that is not deleted.
     * Returns empty if not in this household or already deleted.
     *
     * @param id          the device UUID
     * @param householdId the household UUID
     * @return the device if found within the household and active
     */
    Optional<Device> findByIdAndHouseholdIdAndDeletedAtIsNull(UUID id, UUID householdId);
}
