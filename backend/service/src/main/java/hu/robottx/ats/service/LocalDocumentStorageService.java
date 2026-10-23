package hu.robottx.ats.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import hu.robottx.ats.config.LocalStorageConfig;
import hu.robottx.ats.exception.ResourceNotFoundException;
import hu.robottx.ats.exception.StorageException;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage-provider", havingValue = "local", matchIfMissing = true)
public class LocalDocumentStorageService implements DocumentStorageService {

    private final LocalStorageConfig localStorageConfig;

    @Override
    public void store(String objectKey, InputStream inputStream, long contentLength, String contentType) {
        try {
            Path targetPath = resolvePath(objectKey);
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Stored document locally at key: {}", objectKey);
        } catch (IOException e) {
            log.error("Failed to store document at key: {}", objectKey, e);
            throw new StorageException("Failed to store document", e);
        }
    }

    @Override
    public InputStream load(String objectKey) {
        Path targetPath = resolvePath(objectKey);
        if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
            throw new ResourceNotFoundException("Document not found for key: " + objectKey);
        }
        try {
            return Files.newInputStream(targetPath);
        } catch (IOException e) {
            log.error("Failed to load document at key: {}", objectKey, e);
            throw new StorageException("Failed to load document", e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Path targetPath = resolvePath(objectKey);
            Files.deleteIfExists(targetPath);
            log.debug("Deleted document locally at key: {}", objectKey);
        } catch (IOException e) {
            log.error("Failed to delete document at key: {}", objectKey, e);
            throw new StorageException("Failed to delete document", e);
        }
    }

    @Override
    public void copy(String sourceKey, String destinationKey) {
        try {
            Path sourcePath = resolvePath(sourceKey);
            if (!Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
                throw new ResourceNotFoundException("Source document not found for key: " + sourceKey);
            }
            Path destinationPath = resolvePath(destinationKey);
            if (destinationPath.getParent() != null) {
                Files.createDirectories(destinationPath.getParent());
            }
            Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Copied document locally from {} to {}", sourceKey, destinationKey);
        } catch (IOException e) {
            log.error("Failed to copy document from {} to {}", sourceKey, destinationKey, e);
            throw new StorageException("Failed to copy document", e);
        }
    }

    private Path resolvePath(String objectKey) {
        Path root = Paths.get(localStorageConfig.getDirectory()).toAbsolutePath().normalize();
        Path resolved = root.resolve(objectKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid object key: " + objectKey);
        }
        return resolved;
    }

}
