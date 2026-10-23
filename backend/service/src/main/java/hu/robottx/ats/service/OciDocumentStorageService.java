package hu.robottx.ats.service;

import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.CopyObjectDetails;
import com.oracle.bmc.objectstorage.requests.CopyObjectRequest;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;

import java.io.InputStream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import hu.robottx.ats.config.OciStorageConfig;
import hu.robottx.ats.exception.ResourceNotFoundException;
import hu.robottx.ats.exception.StorageException;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage-provider", havingValue = "oci")
public class OciDocumentStorageService implements DocumentStorageService {

    private static final int HTTP_NOT_FOUND = 404;

    private final ObjectStorageClient objectStorageClient;
    private final OciStorageConfig ociStorageConfig;

    @Override
    public void store(String objectKey, InputStream inputStream, long contentLength, String contentType) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucketName(ociStorageConfig.getBucketName())
                    .namespaceName(ociStorageConfig.getNamespace())
                    .objectName(objectKey)
                    .contentLength(contentLength)
                    .contentType(contentType)
                    .putObjectBody(inputStream)
                    .build();
            objectStorageClient.putObject(request);
            log.debug("Stored document in OCI bucket at key: {}", objectKey);
        } catch (BmcException ex) {
            log.error("Failed to store document in OCI at key: {}", objectKey, ex);
            throw new StorageException("Failed to store document in OCI", ex);
        }
    }

    @Override
    public InputStream load(String objectKey) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucketName(ociStorageConfig.getBucketName())
                    .namespaceName(ociStorageConfig.getNamespace())
                    .objectName(objectKey)
                    .build();
            GetObjectResponse response = objectStorageClient.getObject(request);
            return response.getInputStream();
        } catch (BmcException ex) {
            if (ex.getStatusCode() == HTTP_NOT_FOUND) {
                throw new ResourceNotFoundException("Document not found for key: " + objectKey);
            }
            log.error("Failed to load document from OCI at key: {}", objectKey, ex);
            throw new StorageException("Failed to load document from OCI", ex);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucketName(ociStorageConfig.getBucketName())
                    .namespaceName(ociStorageConfig.getNamespace())
                    .objectName(objectKey)
                    .build();
            objectStorageClient.deleteObject(request);
            log.debug("Deleted document from OCI bucket at key: {}", objectKey);
        } catch (BmcException ex) {
            if (ex.getStatusCode() == HTTP_NOT_FOUND) {
                log.debug("Document at key {} was already deleted or not found in OCI", objectKey);
                return;
            }
            log.error("Failed to delete document from OCI at key: {}", objectKey, ex);
            throw new StorageException("Failed to delete document from OCI", ex);
        }
    }

    @Override
    public void copy(String sourceKey, String destinationKey) {
        try {
            CopyObjectDetails copyDetails = CopyObjectDetails.builder()
                    .sourceObjectName(sourceKey)
                    .destinationObjectName(destinationKey)
                    .destinationBucket(ociStorageConfig.getBucketName())
                    .destinationNamespace(ociStorageConfig.getNamespace())
                    .destinationRegion(ociStorageConfig.getRegion())
                    .build();
            CopyObjectRequest request = CopyObjectRequest.builder()
                    .bucketName(ociStorageConfig.getBucketName())
                    .namespaceName(ociStorageConfig.getNamespace())
                    .copyObjectDetails(copyDetails)
                    .build();
            objectStorageClient.copyObject(request);
            log.debug("Copied document in OCI bucket from {} to {}", sourceKey, destinationKey);
        } catch (BmcException ex) {
            if (ex.getStatusCode() == HTTP_NOT_FOUND) {
                throw new ResourceNotFoundException("Source document not found for key: " + sourceKey);
            }
            log.error("Failed to copy document in OCI from {} to {}", sourceKey, destinationKey, ex);
            throw new StorageException("Failed to copy document in OCI", ex);
        }
    }

}
