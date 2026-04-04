package com.keepr.ingestion.service;

import java.util.UUID;

import com.keepr.common.exception.ErrorCode;
import com.keepr.common.exception.KeeprException;
import com.keepr.common.security.KeeprPrincipal;
import com.keepr.ingestion.dto.JobStatusResponse;
import com.keepr.ingestion.dto.UploadDocumentResponse;
import com.keepr.ingestion.model.ExtractionJob;
import com.keepr.ingestion.repository.ExtractionJobRepository;
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
    private final ExtractionJobRepository extractionJobRepository;

    /**
     * Orchestrates the complete document upload flow.
     * 1. Physical storage (outside main transaction, with server-side MIME detection)
     * 2. Transactional metadata save (via IngestionMetadataService)
     * 3. Rollback cleanup on failure
     *
     * @param file      multipart object from controller
     * @param principal authenticated user principal
     * @return {@link UploadDocumentResponse} containing generated tracking IDs (documentId, jobId)
     */
    public UploadDocumentResponse uploadDocument(MultipartFile file, KeeprPrincipal principal) {
        // Step 1: Storage & MIME Sniffing (Outside Transaction)
        FileStorageService.StoredFile storedFile = fileStorageService.store(file);

        try {
            // Step 2: Database Metadata using server-detected MIME type
            return ingestionMetadataService.saveMetadata(
                    principal.householdId(), 
                    principal.userId(), 
                    storedFile.path(), 
                    storedFile.contentType()
            );
        } catch (Exception e) {
            // Step 3: Cleanup Hook
            log.error("Metadata save failed, cleaning up orphaned file at: {}", storedFile.path(), e);
            try {
                fileStorageService.delete(storedFile.path());
            } catch (Exception deleteEx) {
                e.addSuppressed(deleteEx);
            }
            throw e;
        }
    }

    /**
     * Retrieves the status of an extraction job, scoped to the user's household.
     *
     * @param jobId       the ID of the job to track
     * @param householdId the household ID for security validation
     * @return the current status and tracking details
     * @throws KeeprException if the job is not found or belongs to a different household
     */
    public JobStatusResponse getJobStatus(UUID jobId, UUID householdId) {
        ExtractionJob job = extractionJobRepository.findByIdAndHouseholdId(jobId, householdId)
                .orElseThrow(() -> new KeeprException(ErrorCode.NOT_FOUND, "Extraction job not found"));

        return new JobStatusResponse(job.getId(), job.getStatus(), job.getErrorMessage());
    }
}
