package hu.robottx.ats.service;

import com.oracle.bmc.util.internal.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import hu.robottx.ats.domain.DocumentDownload;
import hu.robottx.ats.entity.Document;
import hu.robottx.ats.exception.StorageException;
import hu.robottx.ats.repository.DocumentRepository;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    public static final long MAX_CV_FILE_SIZE = 5 * 1024 * 1024L;

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(MediaType.APPLICATION_PDF_VALUE);

    private final DocumentRepository documentRepository;
    private final DocumentStorageService documentStorageService;
    private final Clock clock;

    @Override
    @Transactional
    public Document uploadCvDocument(String keyPrefix, String userId, MultipartFile file) {
        validateFile(file);
        String originalFilename = Paths.get(file.getOriginalFilename()).getFileName().toString();
        String safeFilename = getSafeFilename(originalFilename);
        String contentType = file.getContentType();
        String objectKey = buildObjectKey(keyPrefix, userId, safeFilename);
        try (InputStream inputStream = file.getInputStream()) {
            documentStorageService.store(objectKey, inputStream, file.getSize(), contentType);
        } catch (IOException ex) {
            throw new StorageException("Failed to read file", ex);
        }
        Document document = Document.builder()
                .originalDocumentName(originalFilename)
                .mimeType(contentType)
                .uploadedAt(Instant.now(clock))
                .objectKey(objectKey)
                .build();
        return documentRepository.save(document);
    }

    @Override
    @Transactional
    public Document copyCvDocument(String keyPrefix, String userId, Document sourceDocument) {
        String safeFilename = getSafeFilename(sourceDocument.getOriginalDocumentName());
        String newObjectKey = buildObjectKey(keyPrefix, userId, safeFilename);
        documentStorageService.copy(sourceDocument.getObjectKey(), newObjectKey);
        Document document = Document.builder()
                .originalDocumentName(sourceDocument.getOriginalDocumentName())
                .mimeType(sourceDocument.getMimeType())
                .uploadedAt(Instant.now(clock))
                .objectKey(newObjectKey)
                .build();
        return documentRepository.save(document);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDownload downloadDocument(Document document) {
        InputStream inputStream = documentStorageService.load(document.getObjectKey());
        return new DocumentDownload(
                document.getOriginalDocumentName(),
                document.getMimeType(),
                new InputStreamResource(inputStream));
    }

    @Override
    @Transactional
    public void deleteDocument(Document document) {
        documentRepository.delete(document);
        documentStorageService.delete(document.getObjectKey());
    }

    private String buildObjectKey(String keyPrefix, String userId, String safeFilename) {
        return keyPrefix + "/" + userId + "/" + UUID.randomUUID() + "-" + safeFilename;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }
        if (file.getSize() > MAX_CV_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds the maximum allowed size of 5 MB");
        }
        if (StringUtils.isBlank(file.getOriginalFilename())) {
            throw new IllegalArgumentException("File name cannot be empty");
        }
        if (file.getContentType() == null || !ALLOWED_MIME_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("File type not allowed. Supported types: PDF");
        }
    }

    private String getSafeFilename(String filename) {
        return filename.replaceAll("\\s+", "_")
                .replaceAll("[^a-zA-Z0-9._-]", "");
    }

}
