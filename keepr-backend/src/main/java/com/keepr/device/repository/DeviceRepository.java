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
     * Finds all devices belonging to a household, ordered by creation date descending.
     *
     * @param householdId the household UUID
     * @return list of devices ordered newest-first
     */
    List<Device> findAllByHouseholdIdOrderByCreatedAtDesc(UUID householdId);

    /**
     * Finds a device by its ID and household ID. Returns empty if not in this household.
     *
     * @param id          the device UUID
     * @param householdId the household UUID
     * @return the device if found within the household
     */
    Optional<Device> findByIdAndHouseholdId(UUID id, UUID householdId);
}
