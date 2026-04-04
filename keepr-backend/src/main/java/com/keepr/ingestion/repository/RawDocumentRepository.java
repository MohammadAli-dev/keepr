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
     * Finds a document by its ID and household ID for multi-tenancy enforcement.
     *
     * @param id          the document UUID
     * @param householdId the household UUID
     * @return the document if found within the household context
     */
    Optional<RawDocument> findByIdAndHouseholdId(UUID id, UUID householdId);

    /**
     * Finds all documents for a household.
     */
    List<RawDocument> findAllByHouseholdId(UUID householdId);

    /**
     * Override to mark as unsafe.
     * @deprecated Use {@link #findByIdAndHouseholdId(UUID, UUID)} instead.
     */
    @Deprecated(since = "1.0", forRemoval = false)
    @Override
    Optional<RawDocument> findById(@org.springframework.lang.NonNull UUID id);
}
