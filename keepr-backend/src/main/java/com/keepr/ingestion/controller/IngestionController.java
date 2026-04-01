package com.keepr.ingestion.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import com.keepr.common.exception.ErrorCode;
import com.keepr.common.exception.KeeprException;
import com.keepr.common.security.KeeprPrincipal;
import com.keepr.ingestion.dto.JobStatusResponse;
import com.keepr.ingestion.dto.UploadDocumentResponse;
import com.keepr.ingestion.model.ExtractionJob;
import com.keepr.ingestion.model.JobStatus;
import com.keepr.ingestion.model.RawDocument;
import com.keepr.ingestion.repository.ExtractionJobRepository;
import com.keepr.ingestion.repository.RawDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller for document ingestion and job tracking.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Slf4j
public class IngestionController {

    private final RawDocumentRepository rawDocumentRepository;
    private final ExtractionJobRepository extractionJobRepository;

    private static final String UPLOAD_DIR = "/tmp/keepr-uploads";

    /**
     * Uploads a document for asynchronous processing.
     *
     * @param file      the multipart document file
     * @param principal authenticated user
     * @return job tracking details
     */
    @PostMapping("/upload")
    @Transactional
    public ResponseEntity<UploadDocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal KeeprPrincipal principal) {

        validateFile(file);

        String uniqueFileName = UUID.randomUUID() + "-" + file.getOriginalFilename();
        Path targetPath = Path.of(UPLOAD_DIR).resolve(uniqueFileName);

        try {
            Files.createDirectories(Path.of(UPLOAD_DIR));
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to save uploaded file: {}", uniqueFileName, e);
            throw new KeeprException(ErrorCode.INTERNAL_ERROR, "Failed to save file");
        }

        RawDocument doc = new RawDocument();
        doc.setHouseholdId(principal.householdId());
        doc.setFileName(file.getOriginalFilename());
        doc.setFileUrl(targetPath.toString());
        doc.setFileType(file.getContentType());
        doc.setUploadedBy(principal.userId());
        doc = rawDocumentRepository.save(doc);

        ExtractionJob job = new ExtractionJob();
        job.setHouseholdId(principal.householdId());
        job.setRawDocumentId(doc.getId());
        job.setStatus(JobStatus.PENDING);
        job = extractionJobRepository.save(job);

        log.info("Document uploaded & job created: jobId={}, householdId={}", job.getId(), principal.householdId());

        return ResponseEntity.ok(new UploadDocumentResponse(doc.getId(), job.getId(), job.getStatus()));
    }

    /**
     * Gets the status of an extraction job.
     *
     * @param jobId     the job UUID
     * @param principal authenticated user
     * @return job status details
     */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<JobStatusResponse> getJobStatus(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal KeeprPrincipal principal) {

        ExtractionJob job = extractionJobRepository.findByIdAndHouseholdId(jobId, principal.householdId())
                .orElseThrow(() -> new KeeprException(ErrorCode.NOT_FOUND, "Job not found"));

        return ResponseEntity.ok(new JobStatusResponse(job.getId(), job.getStatus(), job.getErrorMessage()));
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new KeeprException(ErrorCode.BAD_REQUEST, "File is empty");
        }
        if (file.getSize() > 10 * 1024 * 1024) { // 10MB limit
            throw new KeeprException(ErrorCode.BAD_REQUEST, "File exceeds 10MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("application/pdf") && !contentType.startsWith("image/"))) {
            throw new KeeprException(ErrorCode.BAD_REQUEST, "Unsupported file type. Only PDF and Images allowed.");
        }
    }
}
