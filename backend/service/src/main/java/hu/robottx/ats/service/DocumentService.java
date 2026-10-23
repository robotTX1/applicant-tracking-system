package hu.robottx.ats.service;

import org.springframework.web.multipart.MultipartFile;

import hu.robottx.ats.domain.DocumentDownload;
import hu.robottx.ats.entity.Document;

public interface DocumentService {

    Document uploadCvDocument(String keyPrefix, String userId, MultipartFile file);

    Document copyCvDocument(String keyPrefix, String userId, Document sourceDocument);

    DocumentDownload downloadDocument(Document document);

    void deleteDocument(Document document);

}
