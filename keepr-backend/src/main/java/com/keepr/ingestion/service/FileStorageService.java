package com.keepr.ingestion.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import com.keepr.common.exception.ErrorCode;
import com.keepr.common.exception.KeeprException;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * Stores a multipart file to the configured upload directory.
     * Uses a strict UUID-based naming scheme with MIME-mapped extensions.
     *
     * @param file the multipart file to store
     * @return the path to the stored file
     */
    public Path store(MultipartFile file) {
        String extension = getSafeExtension(file.getContentType());
        String uniqueFileName = UUID.randomUUID() + "." + extension;
        Path targetPath = Path.of(uploadDir).resolve(uniqueFileName);

        try {
            Files.createDirectories(Path.of(uploadDir));
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return targetPath;
        } catch (IOException e) {
            log.error("Failed to save uploaded file: {}", uniqueFileName, e);
            throw new KeeprException(ErrorCode.INTERNAL_ERROR, "Failed to save file");
        }
    }

    /**
     * Deletes a file from the filesystem.
     *
     * @param filePath path to the file
     */
    public void delete(String filePath) {
        Path uploadDirPath = Path.of(uploadDir).toAbsolutePath().normalize();
        Path resolved = uploadDirPath.resolve(filePath).normalize();
        if (!resolved.startsWith(uploadDirPath)) {
            throw new SecurityException("Invalid file path");
        }
        try {
            Files.deleteIfExists(resolved);
        } catch (IOException e) {
            log.error("Failed to delete file {}", resolved, e);
            throw new KeeprException(ErrorCode.INTERNAL_ERROR, "File deletion failed");
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
