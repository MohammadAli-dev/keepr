package com.keepr.device.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.keepr.device.model.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Finds an active device by its unique identifying fields within a household.
     * Handles NULL safety for name, brand, and model using explicit NULL checks.
     * Used to prevent duplicate device creation in async ingestion workflows.
     *
     * @param name        the device name
     * @param brand       the device brand
     * @param model       the device model
     * @param householdId the household UUID
     * @return the active device if found
     */
    @Query("""
            SELECT d FROM Device d 
            WHERE d.householdId = :householdId 
            AND d.deletedAt IS NULL 
            AND (:name IS NULL OR d.name = :name) AND (d.name IS NULL OR :name IS NOT NULL) 
            AND (:brand IS NULL OR d.brand = :brand) AND (d.brand IS NULL OR :brand IS NOT NULL) 
            AND (:model IS NULL OR d.model = :model) AND (d.model IS NULL OR :model IS NOT NULL)
            """)
    Optional<Device> findByNameAndBrandAndModelAndHouseholdIdAndDeletedAtIsNull(
            @Param("name") String name, 
            @Param("brand") String brand, 
            @Param("model") String model, 
            @Param("householdId") UUID householdId);
}
