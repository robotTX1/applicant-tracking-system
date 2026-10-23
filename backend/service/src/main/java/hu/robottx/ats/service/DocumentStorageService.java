package hu.robottx.ats.service;

import java.io.InputStream;

public interface DocumentStorageService {

    void store(String objectKey, InputStream inputStream, long contentLength, String contentType);

    InputStream load(String objectKey);

    void delete(String objectKey);

    void copy(String sourceKey, String destinationKey);

}
