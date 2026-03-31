package com.keepr.warranty.repository;

import java.util.List;
import java.util.UUID;

import com.keepr.warranty.model.Warranty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link Warranty} entities.
 * All queries are scoped by householdId for strict multi-tenancy.
 */
@Repository
public interface WarrantyRepository extends JpaRepository<Warranty, UUID> {

    /**
     * Finds all warranties belonging to a household that are not deleted, ordered by creation date descending.
     *
     * @param householdId the household UUID
     * @return list of warranties ordered newest-first
     */
    List<Warranty> findAllByHouseholdIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID householdId);

    /**
     * Finds all active warranties for a specific device within a household.
     * Used for overlap validation.
     *
     * @param deviceId    the device UUID
     * @param householdId the household UUID
     * @return list of active warranties for the given device
     */
    List<Warranty> findAllByDeviceIdAndHouseholdIdAndDeletedAtIsNull(UUID deviceId, UUID householdId);
}
