package com.keepr.ingestion.service;

import java.nio.file.Path;
import java.util.UUID;

import com.keepr.common.security.KeeprPrincipal;
import com.keepr.ingestion.model.ExtractionJob;
import com.keepr.ingestion.model.JobStatus;
import com.keepr.ingestion.model.RawDocument;
import com.keepr.ingestion.repository.ExtractionJobRepository;
import com.keepr.ingestion.repository.RawDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service orchestrating the document ingestion flow.
 * Coordinates file storage and multi-step database metadata creation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionService {

    private final RawDocumentRepository rawDocumentRepository;
    private final ExtractionJobRepository extractionJobRepository;
    private final FileStorageService fileStorageService;

    /**
     * Orchestrates the complete document upload flow.
     * 1. Physical storage (outside main transaction)
     * 2. Transactional metadata save
     * 3. Rollback cleanup on failure
     *
     * @param file      multipart object from controller
     * @param principal authenticated user principal
     * @return tracking details of the new extraction job
     */
    public ExtractionJob uploadDocument(MultipartFile file, KeeprPrincipal principal) {
        // Step 1: Storage (Outside Transaction)
        Path targetPath = fileStorageService.store(file);

        try {
            // Step 2: Database Metadata (Transactional)
            return saveMetadata(targetPath, principal.householdId(), principal.userId(), file.getContentType());
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

    /**
     * Saves record metadata for a newly uploaded document and its job.
     * Internal transactional atomic step.
     *
     * @param targetPath   physical location on disk
     * @param householdId  associated household ID
     * @param uploadedBy   uploader's user ID
     * @param contentType  mime type of the file
     * @return the newly created extraction job
     */
    @Transactional
    public ExtractionJob saveMetadata(Path targetPath, UUID householdId, UUID uploadedBy, String contentType) {
        if (targetPath == null) {
            throw new IllegalArgumentException("targetPath cannot be null");
        }
        if (householdId == null) {
            throw new IllegalArgumentException("householdId cannot be null");
        }
        if (uploadedBy == null) {
            throw new IllegalArgumentException("uploadedBy cannot be null");
        }
        if (contentType == null) {
            throw new IllegalArgumentException("contentType cannot be null");
        }

        String fileName = targetPath.getFileName() != null 
                ? targetPath.getFileName().toString() 
                : targetPath.toString();

        RawDocument doc = new RawDocument();
        doc.setHouseholdId(householdId);
        doc.setFileName(fileName);
        doc.setFileUrl(targetPath.toString());
        doc.setFileType(contentType);
        doc.setUploadedBy(uploadedBy);
        doc = rawDocumentRepository.save(doc);

        ExtractionJob job = new ExtractionJob();
        job.setHouseholdId(householdId);
        job.setRawDocumentId(doc.getId());
        job.setStatus(JobStatus.PENDING);
        return extractionJobRepository.save(job);
    }
}
