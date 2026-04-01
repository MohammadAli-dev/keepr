package com.keepr.ingestion.repository;

import java.util.UUID;
import com.keepr.ingestion.model.RawDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for RawDocument entity.
 */
@Repository
public interface RawDocumentRepository extends JpaRepository<RawDocument, UUID> {
}
