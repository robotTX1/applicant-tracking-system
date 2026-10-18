package hu.robottx.ats.service;

import hu.robottx.ats.model.JobListingResponse;
import hu.robottx.ats.model.JobListingSaveRequest;
import hu.robottx.ats.model.JobListingUpdateRequest;
import hu.robottx.ats.model.PagedJobListingResponse;
import hu.robottx.ats.model.SearchAndPageRequest;

public interface JobListingService {

    PagedJobListingResponse getJobListings(SearchAndPageRequest request);

    JobListingResponse getJobListingById(Long jobListingId);

    JobListingResponse saveJobListing(JobListingSaveRequest request);

    JobListingResponse updateJobListing(Long jobListingId, JobListingUpdateRequest request);

    void deleteJobListing(Long jobListingId);

}
