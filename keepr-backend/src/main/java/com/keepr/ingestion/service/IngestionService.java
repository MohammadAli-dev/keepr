package com.keepr.ingestion.service;

import java.nio.file.Path;

import com.keepr.common.security.KeeprPrincipal;
import com.keepr.ingestion.dto.UploadDocumentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service orchestrating the document ingestion flow.
 * Coordinates file storage and delegates metadata persistence to ensure transactional safety.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionService {

    private final IngestionMetadataService ingestionMetadataService;
    private final FileStorageService fileStorageService;

    /**
     * Orchestrates the complete document upload flow.
     * 1. Physical storage (outside main transaction)
     * 2. Transactional metadata save (via IngestionMetadataService)
     * 3. Rollback cleanup on failure
     *
     * @param file      multipart object from controller
     * @param principal authenticated user principal
     * @return {@link UploadDocumentResponse} containing generated tracking IDs (documentId, jobId)
     */
    public UploadDocumentResponse uploadDocument(MultipartFile file, KeeprPrincipal principal) {
        // Step 1: Storage (Outside Transaction)
        Path targetPath = fileStorageService.store(file);

        try {
            // Step 2: Database Metadata (Transactional delegate)
            return ingestionMetadataService.saveMetadata(
                    principal.householdId(), 
                    principal.userId(), 
                    targetPath.toString(), 
                    file.getContentType()
            );
        } catch (Exception e) {
            // Step 3: Cleanup Hook
            log.error("Metadata save failed, cleaning up orphaned file: {}", targetPath, e);
            try {
                fileStorageService.delete(targetPath.toString());
            } catch (Exception deleteEx) {
                e.addSuppressed(deleteEx);
            }
            throw e;
        }
    }
}
