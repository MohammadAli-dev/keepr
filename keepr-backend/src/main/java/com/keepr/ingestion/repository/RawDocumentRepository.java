package com.keepr.ingestion.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.keepr.ingestion.model.RawDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for RawDocument entity.
 */
@Repository
public interface RawDocumentRepository extends JpaRepository<RawDocument, UUID> {

    /**
     * Secures findById by scoping it to the household.
     */
    Optional<RawDocument> findByIdAndHouseholdId(UUID id, UUID householdId);

    /**
     * Finds all documents for a household.
     */
    List<RawDocument> findAllByHouseholdId(UUID householdId);

    /**
     * Override to mark as unsafe.
     */
    @Deprecated(since = "use findByIdAndHouseholdId instead", forRemoval = false)
    @Override
    Optional<RawDocument> findById(UUID id);
}
