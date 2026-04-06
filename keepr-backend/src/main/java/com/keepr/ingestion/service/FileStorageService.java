package com.keepr.ingestion.service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

import com.keepr.common.exception.ErrorCode;
import com.keepr.common.exception.KeeprException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for physical file operations.
 * Handles storage outside of database transactions.
 */
@Service
@Slf4j
public class FileStorageService {

    @Value("${keepr.upload.dir:/tmp/keepr-uploads}")
    private String uploadDir;

    @Value("${keepr.upload.max-file-size:10MB}")
    private String maxFileSizeStr;

    private long maxFileSizeBytes;

    private final Tika tika = new Tika();

    private static final int MIME_SNIFF_THRESHOLD = 16384; // 16KB
    private static final List<String> ALLOWED_TYPES = List.of(
            "application/pdf",
            "image/jpeg",
            "image/png"
    );

    /**
     * Validates upload directory and configuration at startup.
     */
    @PostConstruct
    public void init() {
        Path path = Path.of(uploadDir);
        if (uploadDir.startsWith("/tmp")) {
            log.warn("Upload directory is set to ephemeral storage: {}", uploadDir);
        }
        try {
            Files.createDirectories(path);
            log.info("File storage initialized at: {}", path.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to initialize upload directory: {}", uploadDir, e);
        }

        try {
            this.maxFileSizeBytes = parseSize(maxFileSizeStr);
            if (this.maxFileSizeBytes <= 0) {
                throw new IllegalArgumentException("Upload size limit must be positive: " + maxFileSizeStr);
            }
            log.info("File upload limit initialized: {} ({} bytes)", maxFileSizeStr, maxFileSizeBytes);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to parse upload size limit configuration '{}' " 
                            + "(key: keepr.upload.max-file-size): {}", 
                    maxFileSizeStr, e.getMessage());
            throw new IllegalArgumentException("Invalid file upload size configuration", e);
        }
    }

    /**
     * DTO containing details of a stored file.
     */
    public record StoredFile(String path, String contentType) {}

    /**
     * Stores a multipart file to the configured upload directory.
     * Uses server-side MIME detection from a 16KB prefix.
     *
     * @param file the multipart file to store
     * @return StoredFile details (path and detected content type)
     */
    public StoredFile store(MultipartFile file) {
        validateSize(file);
        try (InputStream is = file.getInputStream();
                BufferedInputStream bis = new BufferedInputStream(is)) {
            
            // 1. Safe MIME Sniffing (Bounded 16KB)
            bis.mark(MIME_SNIFF_THRESHOLD);
            byte[] prefix = bis.readNBytes(MIME_SNIFF_THRESHOLD);
            bis.reset();

            String detected = tika.detect(prefix);
            log.debug("Detected MIME type: {} for file: {}", detected, file.getOriginalFilename());

            if ("application/octet-stream".equals(detected)) {
                log.warn("Unknown binary (octet-stream) detected, rejecting: {}", file.getOriginalFilename());
                throw new KeeprException(ErrorCode.BAD_REQUEST, "Unsupported file type: Unknown binary");
            }

            if (!ALLOWED_TYPES.contains(detected)) {
                log.warn("Rejected upload. Detected MIME={}, filename={}", detected, file.getOriginalFilename());
                throw new KeeprException(ErrorCode.BAD_REQUEST, "Unsupported file type: " + detected);
            }

            // 2. Storage
            String extension = getSafeExtension(detected);
            String uniqueFileName = UUID.randomUUID() + "." + extension;
            Path targetPath = Path.of(uploadDir).toAbsolutePath().resolve(uniqueFileName);

            Files.copy(bis, targetPath, StandardCopyOption.REPLACE_EXISTING);
            return new StoredFile(targetPath.toString(), detected);

        } catch (IOException e) {
            log.error("Failed to save uploaded file: {}", file.getOriginalFilename(), e);
            throw new KeeprException(ErrorCode.INTERNAL_ERROR, "Failed to save file");
        }
    }

    /**
     * Deletes a file from the filesystem.
     *
     * @param filePath absolute string path to the file
     */
    public void delete(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }

        Path pathToDelete = Path.of(filePath).toAbsolutePath().normalize();
        Path uploadDirPath = Path.of(uploadDir).toAbsolutePath().normalize();

        // Security check: ensure path is within upload directory
        if (!pathToDelete.startsWith(uploadDirPath)) {
            log.error("Security violation: Attempted to delete file outside upload directory: {}", filePath);
            throw new SecurityException("Invalid file path");
        }

        try {
            Files.deleteIfExists(pathToDelete);
            log.info("Successfully deleted file: {}", pathToDelete);
        } catch (IOException e) {
            log.error("Failed to delete file: {}", pathToDelete, e);
            throw new KeeprException(ErrorCode.INTERNAL_ERROR, "File deletion failed");
        }
    }

    private void validateSize(MultipartFile file) {
        if (file.getSize() > maxFileSizeBytes) {
            log.warn("Rejected upload: size={} bytes exceeds max={} bytes for file={}", 
                    file.getSize(), maxFileSizeBytes, file.getOriginalFilename());
            throw new KeeprException(ErrorCode.BAD_REQUEST, "File too large: maximum size is " + maxFileSizeStr);
        }
    }

    private long parseSize(String sizeStr) {
        if (sizeStr == null || sizeStr.isBlank()) {
            throw new IllegalArgumentException("Size configuration string cannot be null or empty");
        }
        String s = sizeStr.toUpperCase().trim();
        try {
            if (s.endsWith("MB")) {
                return Long.parseLong(s.substring(0, s.length() - 2).trim()) * 1024 * 1024;
            } else if (s.endsWith("KB")) {
                return Long.parseLong(s.substring(0, s.length() - 2).trim()) * 1024;
            }
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Failed to parse size configuration: " + sizeStr, e);
        }
    }

    private String getSafeExtension(String contentType) {
        if (contentType == null) {
            throw new KeeprException(ErrorCode.BAD_REQUEST, "Missing content type");
        }
        return switch (contentType) {
            case "application/pdf" -> "pdf";
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> throw new KeeprException(ErrorCode.BAD_REQUEST, "Unsupported file type: " + contentType);
        };
    }
}
