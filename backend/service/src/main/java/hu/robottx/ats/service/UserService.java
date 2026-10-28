package hu.robottx.ats.service;

import org.springframework.web.multipart.MultipartFile;

import hu.robottx.ats.domain.DocumentDownload;
import hu.robottx.ats.model.CurrentUserResponse;
import hu.robottx.ats.model.DocumentResponse;
import hu.robottx.ats.model.PagedUserResponse;
import hu.robottx.ats.model.SearchAndPageRequest;

public interface UserService {

    PagedUserResponse getUsers(SearchAndPageRequest request);

    CurrentUserResponse getCurrentUser();

    DocumentResponse uploadCurrentUserCv(MultipartFile file);

    DocumentDownload getCurrentUserCv();

    DocumentResponse getCurrentUserCvMetadata();

    void deleteCurrentUserCv();

}
