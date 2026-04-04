package com.keepr.ingestion.service;

import java.util.UUID;

import com.keepr.ingestion.dto.UploadDocumentResponse;
import com.keepr.ingestion.model.ExtractionJob;
import com.keepr.ingestion.model.JobStatus;
import com.keepr.ingestion.model.RawDocument;
import com.keepr.ingestion.repository.ExtractionJobRepository;
import com.keepr.ingestion.repository.RawDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service dedicated to atomic database metadata persistence for the ingestion pipeline.
 * Isolated from file system I/O to ensure clean transaction boundaries.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionMetadataService {

    private final RawDocumentRepository rawDocumentRepository;
    private final ExtractionJobRepository extractionJobRepository;

    /**
     * Saves record metadata for a newly uploaded document and its tracking job.
     * This operation is atomic and strictly database-only.
     *
     * @param householdId the associated household ID
     * @param uploadedBy  the uploader's user ID
     * @param filePath    the physical path on disk
     * @param fileType   the mime type of the file
     * @return response details for the newly created job
     */
    @Transactional
    public UploadDocumentResponse saveMetadata(UUID householdId, UUID uploadedBy, String filePath, String fileType) {
        log.info("Saving metadata for document in household: {}", householdId);

        RawDocument doc = new RawDocument();
        doc.setHouseholdId(householdId);
        doc.setFileName(getFileNameFromPath(filePath));
        doc.setFileUrl(filePath);
        doc.setFileType(fileType);
        doc.setUploadedBy(uploadedBy);
        doc = rawDocumentRepository.save(doc);

        ExtractionJob job = new ExtractionJob();
        job.setHouseholdId(householdId);
        job.setRawDocumentId(doc.getId());
        job.setStatus(JobStatus.PENDING);
        job = extractionJobRepository.save(job);

        return new UploadDocumentResponse(doc.getId(), job.getId(), job.getStatus());
    }

    private String getFileNameFromPath(String filePath) {
        if (filePath == null) {
            return "unknown";
        }
        int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }
}
