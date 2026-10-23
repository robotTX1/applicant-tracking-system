package hu.robottx.ats.service;

import org.springframework.web.multipart.MultipartFile;

import hu.robottx.ats.domain.DocumentDownload;
import hu.robottx.ats.model.CurrentUserResponse;
import hu.robottx.ats.model.DocumentResponse;

public interface UserService {

    CurrentUserResponse getCurrentUser();

    DocumentResponse uploadCurrentUserCv(MultipartFile file);

    DocumentDownload getCurrentUserCv();

    DocumentResponse getCurrentUserCvMetadata();

    void deleteCurrentUserCv();

}
