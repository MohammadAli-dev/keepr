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
     * Finds all warranties belonging to a household, ordered by creation date descending.
     *
     * @param householdId the household UUID
     * @return list of warranties ordered newest-first
     */
    List<Warranty> findAllByHouseholdIdOrderByCreatedAtDesc(UUID householdId);
}
