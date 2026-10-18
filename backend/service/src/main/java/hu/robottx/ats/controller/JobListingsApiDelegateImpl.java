package hu.robottx.ats.controller;

import static hu.robottx.ats.controller.RestControllerConstants.JOB_LISTING_BY_ID_ENDPOINT;

import java.net.URI;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import hu.robottx.ats.api.JobListingsApiDelegate;
import hu.robottx.ats.model.JobListingResponse;
import hu.robottx.ats.model.JobListingSaveRequest;
import hu.robottx.ats.model.JobListingUpdateRequest;
import hu.robottx.ats.model.PagedJobListingResponse;
import hu.robottx.ats.model.SearchAndPageRequest;
import hu.robottx.ats.service.JobListingService;

@Component
@RequiredArgsConstructor
public class JobListingsApiDelegateImpl implements JobListingsApiDelegate {

    private final JobListingService jobListingService;

    @Override
    public ResponseEntity<PagedJobListingResponse> getJobListings(SearchAndPageRequest searchAndPageRequest) {
        PagedJobListingResponse response = jobListingService.getJobListings(searchAndPageRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<JobListingResponse> getJobListing(Long jobListingId) {
        JobListingResponse response = jobListingService.getJobListingById(jobListingId);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<JobListingResponse> saveJobListing(JobListingSaveRequest jobListingSaveRequest) {
        JobListingResponse response = jobListingService.saveJobListing(jobListingSaveRequest);
        return ResponseEntity.created(buildUri(response.getId())).body(response);
    }

    @Override
    public ResponseEntity<JobListingResponse> updateJobListing(Long jobListingId,
            JobListingUpdateRequest jobListingUpdateRequest) {
        JobListingResponse response = jobListingService.updateJobListing(jobListingId, jobListingUpdateRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteJobListing(Long jobListingId) {
        jobListingService.deleteJobListing(jobListingId);
        return ResponseEntity.noContent().build();
    }

    private URI buildUri(Long jobListingId) {
        return UriComponentsBuilder.fromUriString(JOB_LISTING_BY_ID_ENDPOINT)
                .buildAndExpand(jobListingId)
                .toUri();
    }

}
