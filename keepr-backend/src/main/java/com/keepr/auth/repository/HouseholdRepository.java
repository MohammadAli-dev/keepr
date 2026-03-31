package com.keepr.auth.repository;

import java.util.Optional;
import java.util.UUID;

import com.keepr.auth.model.Household;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link Household} entities.
 */
@Repository
public interface HouseholdRepository extends JpaRepository<Household, UUID> {

    /**
     * Finds a household by its owner user ID.
     *
     * @param ownerUserId the owner's user ID
     * @return the household if found
     */
    Optional<Household> findByOwnerUserId(UUID ownerUserId);
}
