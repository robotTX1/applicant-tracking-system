package hu.robottx.ats.controller;

import static hu.robottx.ats.controller.RestControllerConstants.CURRENT_USER_APPLICATION_BY_ID_ENDPOINT;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import lombok.RequiredArgsConstructor;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import hu.robottx.ats.api.ApplicationsApiDelegate;
import hu.robottx.ats.domain.DocumentDownload;
import hu.robottx.ats.model.ApplicationSaveRequest;
import hu.robottx.ats.model.DocumentResponse;
import hu.robottx.ats.model.JobApplicationResponse;
import hu.robottx.ats.model.PagedJobApplicationResponse;
import hu.robottx.ats.model.PagedUserApplicationResponse;
import hu.robottx.ats.model.SearchAndPageRequest;
import hu.robottx.ats.model.UserApplicationResponse;
import hu.robottx.ats.service.ApplicationService;

@Component
@RequiredArgsConstructor
public class ApplicationsApiDelegateImpl implements ApplicationsApiDelegate {

    private final ApplicationService applicationService;

    @Override
    public ResponseEntity<PagedUserApplicationResponse> getCurrentUserApplications(
            SearchAndPageRequest searchAndPageRequest) {
        return ResponseEntity.ok(applicationService.getCurrentUserApplications(searchAndPageRequest));
    }

    @Override
    public ResponseEntity<UserApplicationResponse> saveCurrentUserApplication(
            ApplicationSaveRequest request, MultipartFile file) {
        UserApplicationResponse response = applicationService.saveCurrentUserApplication(request, file);
        return ResponseEntity.created(buildUri(response.getId())).body(response);
    }

    @Override
    public ResponseEntity<UserApplicationResponse> getCurrentUserApplication(Long applicationId) {
        return ResponseEntity.ok(applicationService.getCurrentUserApplicationById(applicationId));
    }

    @Override
    public ResponseEntity<Void> deleteCurrentUserApplication(Long applicationId) {
        applicationService.deleteCurrentUserApplication(applicationId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Resource> getCurrentUserApplicationCv(Long applicationId) {
        return toResourceResponse(applicationService.getCurrentUserApplicationCv(applicationId));
    }

    @Override
    public ResponseEntity<DocumentResponse> getCurrentUserApplicationCvMetadata(Long applicationId) {
        return ResponseEntity.ok(applicationService.getCurrentUserApplicationCvMetadata(applicationId));
    }

    @Override
    public ResponseEntity<PagedJobApplicationResponse> getJobListingApplications(
            Long jobListingId, SearchAndPageRequest searchAndPageRequest) {
        return ResponseEntity.ok(applicationService.getJobListingApplications(jobListingId, searchAndPageRequest));
    }

    @Override
    public ResponseEntity<JobApplicationResponse> getJobListingApplication(Long jobListingId, Long applicationId) {
        return ResponseEntity.ok(applicationService.getJobListingApplicationById(jobListingId, applicationId));
    }

    @Override
    public ResponseEntity<Resource> getJobListingApplicationCv(Long jobListingId, Long applicationId) {
        return toResourceResponse(applicationService.getJobListingApplicationCv(jobListingId, applicationId));
    }

    @Override
    public ResponseEntity<DocumentResponse> getJobListingApplicationCvMetadata(Long jobListingId, Long applicationId) {
        return ResponseEntity.ok(applicationService.getJobListingApplicationCvMetadata(jobListingId, applicationId));
    }

    private URI buildUri(Long applicationId) {
        return UriComponentsBuilder.fromUriString(CURRENT_USER_APPLICATION_BY_ID_ENDPOINT)
                .buildAndExpand(applicationId)
                .toUri();
    }

    private ResponseEntity<Resource> toResourceResponse(DocumentDownload download) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.filename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(download.resource());
    }

}
