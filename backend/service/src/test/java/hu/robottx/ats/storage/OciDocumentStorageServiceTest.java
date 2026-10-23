package hu.robottx.ats.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.CopyObjectRequest;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import hu.robottx.ats.UnitTest;
import hu.robottx.ats.config.OciStorageConfig;
import hu.robottx.ats.exception.ResourceNotFoundException;
import hu.robottx.ats.exception.StorageException;
import hu.robottx.ats.service.OciDocumentStorageService;

class OciDocumentStorageServiceTest extends UnitTest {

    @Mock
    private ObjectStorageClient objectStorageClient;

    private OciDocumentStorageService storageService;

    @BeforeEach
    void setUp() {
        OciStorageConfig ociStorageConfig = new OciStorageConfig();
        ociStorageConfig.setBucketName("test-bucket");
        ociStorageConfig.setNamespace("test-namespace");
        ociStorageConfig.setRegion("eu-frankfurt-1");
        storageService = new OciDocumentStorageService(objectStorageClient, ociStorageConfig);
    }

    @Test
    void testStore() {
        byte[] content = "test content".getBytes(StandardCharsets.UTF_8);
        when(objectStorageClient.putObject(any(PutObjectRequest.class)))
                .thenReturn(PutObjectResponse.builder().build());
        storageService.store("cv/1.pdf", new ByteArrayInputStream(content), content.length, "application/pdf");
        verify(objectStorageClient).putObject(any(PutObjectRequest.class));
    }

    @Test
    void testStoreThrowsStorageExceptionOnBmcException() {
        byte[] content = "test content".getBytes(StandardCharsets.UTF_8);
        when(objectStorageClient.putObject(any(PutObjectRequest.class)))
                .thenThrow(new BmcException(500, "InternalError", "Error", "req-1"));
        ByteArrayInputStream stream = new ByteArrayInputStream(content);
        assertThatThrownBy(() -> storageService.store("cv/1.pdf", stream, content.length, "application/pdf"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to store document in OCI");
    }

    @Test
    void testLoad() throws Exception {
        byte[] content = "loaded content".getBytes(StandardCharsets.UTF_8);
        GetObjectResponse response = GetObjectResponse.builder()
                .inputStream(new ByteArrayInputStream(content))
                .build();
        when(objectStorageClient.getObject(any(GetObjectRequest.class))).thenReturn(response);
        try (InputStream result = storageService.load("cv/1.pdf")) {
            assertThat(result.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void testLoadNotFoundThrowsResourceNotFoundException() {
        when(objectStorageClient.getObject(any(GetObjectRequest.class)))
                .thenThrow(new BmcException(404, "NotFound", "Not found", "req-1"));
        assertThatThrownBy(() -> storageService.load("cv/missing.pdf"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Document not found for key: cv/missing.pdf");
    }

    @Test
    void testDelete() {
        storageService.delete("cv/1.pdf");
        verify(objectStorageClient).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void testDeleteNotFoundDoesNotThrow() {
        when(objectStorageClient.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(new BmcException(404, "NotFound", "Not found", "req-1"));
        storageService.delete("cv/missing.pdf");
    }

    @Test
    void testCopy() {
        storageService.copy("cv/source.pdf", "cv/dest.pdf");
        verify(objectStorageClient).copyObject(any(CopyObjectRequest.class));
    }

    @Test
    void testCopySourceNotFoundThrowsResourceNotFoundException() {
        when(objectStorageClient.copyObject(any(CopyObjectRequest.class)))
                .thenThrow(new BmcException(404, "NotFound", "Not found", "req-1"));
        assertThatThrownBy(() -> storageService.copy("cv/missing.pdf", "cv/dest.pdf"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Source document not found for key: cv/missing.pdf");
    }

}
