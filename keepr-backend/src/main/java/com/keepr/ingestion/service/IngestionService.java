package com.keepr.ingestion.service;

import java.nio.file.Path;
import java.util.UUID;

import com.keepr.ingestion.model.ExtractionJob;
import com.keepr.ingestion.model.JobStatus;
import com.keepr.ingestion.model.RawDocument;
import com.keepr.ingestion.repository.ExtractionJobRepository;
import com.keepr.ingestion.repository.RawDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for transactional database operations inside the ingestion flow.
 * Coordinates entity creation for raw documents and extraction jobs.
 */
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final RawDocumentRepository rawDocumentRepository;
    private final ExtractionJobRepository extractionJobRepository;

    /**
     * Saves record metadata for a newly uploaded document and its job.
     *
     * @param targetPath   physical location on disk
     * @param householdId  associated household ID
     * @param uploadedBy   uploader's user ID
     * @param contentType  mime type of the file
     * @return the newly created extraction job
     */
    @Transactional
    public ExtractionJob saveMetadata(Path targetPath, UUID householdId, UUID uploadedBy, String contentType) {
        RawDocument doc = new RawDocument();
        doc.setHouseholdId(householdId);
        doc.setFileName(targetPath.getFileName().toString());
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
