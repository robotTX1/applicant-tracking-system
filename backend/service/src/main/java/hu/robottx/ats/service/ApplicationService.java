package hu.robottx.ats.service;

import org.springframework.web.multipart.MultipartFile;

import hu.robottx.ats.domain.DocumentDownload;
import hu.robottx.ats.model.ApplicationSaveRequest;
import hu.robottx.ats.model.DocumentResponse;
import hu.robottx.ats.model.JobApplicationResponse;
import hu.robottx.ats.model.PagedJobApplicationResponse;
import hu.robottx.ats.model.PagedUserApplicationResponse;
import hu.robottx.ats.model.SearchAndPageRequest;
import hu.robottx.ats.model.UserApplicationResponse;

public interface ApplicationService {

    PagedUserApplicationResponse getCurrentUserApplications(SearchAndPageRequest request);

    UserApplicationResponse saveCurrentUserApplication(ApplicationSaveRequest request, MultipartFile file);

    UserApplicationResponse getCurrentUserApplicationById(Long applicationId);

    void deleteCurrentUserApplication(Long applicationId);

    DocumentDownload getCurrentUserApplicationCv(Long applicationId);

    DocumentResponse getCurrentUserApplicationCvMetadata(Long applicationId);

    PagedJobApplicationResponse getJobListingApplications(Long jobListingId, SearchAndPageRequest request);

    JobApplicationResponse getJobListingApplicationById(Long jobListingId, Long applicationId);

    DocumentDownload getJobListingApplicationCv(Long jobListingId, Long applicationId);

    DocumentResponse getJobListingApplicationCvMetadata(Long jobListingId, Long applicationId);

}
