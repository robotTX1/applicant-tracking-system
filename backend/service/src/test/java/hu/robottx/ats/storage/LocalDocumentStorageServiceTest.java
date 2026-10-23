package hu.robottx.ats.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hu.robottx.ats.UnitTest;
import hu.robottx.ats.config.LocalStorageConfig;
import hu.robottx.ats.exception.ResourceNotFoundException;
import hu.robottx.ats.service.LocalDocumentStorageService;

class LocalDocumentStorageServiceTest extends UnitTest {

    @TempDir
    private Path tempDir;

    private LocalDocumentStorageService storageService;

    @BeforeEach
    void setUp() {
        LocalStorageConfig config = new LocalStorageConfig();
        config.setDirectory(tempDir.toString());
        storageService = new LocalDocumentStorageService(config);
    }

    @Test
    void testStoreAndLoad() throws Exception {
        byte[] content = "Hello World PDF".getBytes(StandardCharsets.UTF_8);
        String objectKey = "cv/user1/test.pdf";
        storageService.store(objectKey, new ByteArrayInputStream(content), content.length, "application/pdf");
        try (InputStream inputStream = storageService.load(objectKey)) {
            byte[] loadedContent = inputStream.readAllBytes();
            assertThat(loadedContent).isEqualTo(content);
        }
    }

    @Test
    void testLoadNotFound() {
        assertThatThrownBy(() -> storageService.load("cv/nonexistent.pdf"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Document not found for key: cv/nonexistent.pdf");
    }

    @Test
    void testDelete() {
        byte[] content = "Content to delete".getBytes(StandardCharsets.UTF_8);
        String objectKey = "cv/user1/delete.pdf";
        storageService.store(objectKey, new ByteArrayInputStream(content), content.length, "application/pdf");
        storageService.delete(objectKey);
        assertThatThrownBy(() -> storageService.load(objectKey))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void testDeleteNonExistentDoesNotFail() {
        storageService.delete("cv/user1/nonexistent.pdf");
    }

    @Test
    void testCopy() throws Exception {
        byte[] content = "Content to copy".getBytes(StandardCharsets.UTF_8);
        String sourceKey = "cv/user1/source.pdf";
        String destKey = "cv/user1/dest.pdf";
        storageService.store(sourceKey, new ByteArrayInputStream(content), content.length, "application/pdf");
        storageService.copy(sourceKey, destKey);
        try (InputStream inputStream = storageService.load(destKey)) {
            byte[] loadedContent = inputStream.readAllBytes();
            assertThat(loadedContent).isEqualTo(content);
        }
    }

    @Test
    void testCopySourceNotFound() {
        assertThatThrownBy(() -> storageService.copy("cv/nonexistent.pdf", "cv/dest.pdf"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Source document not found for key: cv/nonexistent.pdf");
    }

    @Test
    void testPathTraversalRejected() {
        byte[] content = "malicious".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> storageService.store("../../etc/passwd",
                new ByteArrayInputStream(content), content.length, "text/plain"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid object key");
    }

}
