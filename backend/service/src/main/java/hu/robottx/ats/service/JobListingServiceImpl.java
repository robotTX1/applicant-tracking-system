package hu.robottx.ats.service;

import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hu.robottx.ats.domain.CompanyPermission;
import hu.robottx.ats.domain.JobStatus;
import hu.robottx.ats.entity.Company;
import hu.robottx.ats.entity.JobListing;
import hu.robottx.ats.exception.ResourceNotFoundException;
import hu.robottx.ats.mapper.JobListingMapper;
import hu.robottx.ats.model.JobListingResponse;
import hu.robottx.ats.model.JobListingSaveRequest;
import hu.robottx.ats.model.JobListingStatus;
import hu.robottx.ats.model.JobListingUpdateRequest;
import hu.robottx.ats.model.PagedJobListingResponse;
import hu.robottx.ats.model.SearchAndPageRequest;
import hu.robottx.ats.repository.CompanyRepository;
import hu.robottx.ats.repository.JobListingRepository;
import hu.robottx.ats.security.service.CompanySecurityService;
import hu.robottx.ats.security.service.SecurityService;
import hu.robottx.ats.util.JobListingQueryParamsUtil;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobListingServiceImpl implements JobListingService {

    private static final String JOB_LISTING_NOT_FOUND_MESSAGE = "No job listing with id: %d";

    private final SecurityService securityService;
    private final CompanySecurityService companySecurityService;
    private final CompanyRepository companyRepository;
    private final JobListingRepository jobListingRepository;

    @Override
    @Transactional(readOnly = true)
    public PagedJobListingResponse getJobListings(SearchAndPageRequest request) {
        String userId = securityService.getId();
        log.debug("Getting job listings for user {} with request {}", userId, request);
        Set<Long> authorizedCompanyIds =
                companySecurityService.getCompanyIdsWithPermission(CompanyPermission.VIEW_INTERNAL_JOB_LISTINGS);
        PageRequest pageRequest = PageRequest.of(request.getPageNumber(), request.getPageSize());
        Specification<JobListing> spec = JobListingQueryParamsUtil.createSearchSpecification(
                authorizedCompanyIds, request);
        try {
            Page<JobListing> jobListings = jobListingRepository.findAll(spec, pageRequest);
            return JobListingMapper.INSTANCE.toModel(jobListings);
        } catch (RuntimeException ex) {
            log.debug("Invalid query or sort parameters: query='{}', sort='{}'",
                    request.getQuery(), request.getSort());
            throw new IllegalArgumentException("Invalid query or sort parameters", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public JobListingResponse getJobListingById(Long jobListingId) {
        String userId = securityService.getId();
        log.debug("Getting job listing {} by user {}", jobListingId, userId);
        JobListing jobListing = getJobListing(jobListingId);
        if (jobListing.getStatus() != JobStatus.PUBLISHED
                && !companySecurityService.hasPermission(
                jobListing.getCompany().getCompanyId(), CompanyPermission.VIEW_INTERNAL_JOB_LISTINGS)) {
            log.debug("User {} cannot access non-published job listing {}", userId, jobListingId);
            throw new ResourceNotFoundException(JOB_LISTING_NOT_FOUND_MESSAGE.formatted(jobListingId));
        }
        return JobListingMapper.INSTANCE.toModel(jobListing);
    }

    @Override
    @Transactional
    public JobListingResponse saveJobListing(JobListingSaveRequest request) {
        String userId = securityService.getId();
        log.debug("Creating job listing for company {} by user {}", request.getCompanyId(), userId);
        companySecurityService.validatePermission(request.getCompanyId(), CompanyPermission.MANAGE_JOB_LISTINGS);
        if (request.getStatus() == JobListingStatus.CLOSED) {
            log.debug("Attempted to create job listing with CLOSED status");
            throw new IllegalArgumentException("Cannot create a job listing with CLOSED status");
        }
        JobStatus status = request.getStatus() != null
                           ? JobListingMapper.INSTANCE.toDomain(request.getStatus())
                           : JobStatus.DRAFT;
        Company company = companyRepository.getReferenceById(request.getCompanyId());
        JobListing jobListing = JobListing.builder()
                .company(company)
                .location(request.getLocation())
                .title(request.getTitle())
                .description(request.getDescription())
                .aiReviewEnabled(Boolean.TRUE.equals(request.getAiReviewEnabled()))
                .aiReviewMinScore(request.getAiReviewMinScore())
                .status(status)
                .build();
        jobListing = jobListingRepository.save(jobListing);
        log.debug("Saved job listing with id {} for company {}",
                jobListing.getJobListingId(), request.getCompanyId());
        return JobListingMapper.INSTANCE.toModel(jobListing);
    }

    @Override
    @Transactional
    public JobListingResponse updateJobListing(Long jobListingId, JobListingUpdateRequest request) {
        String userId = securityService.getId();
        log.debug("Updating job listing {} by user {}", jobListingId, userId);
        JobListing jobListing = getJobListingForManager(jobListingId, userId);
        jobListing.setLocation(request.getLocation());
        jobListing.setTitle(request.getTitle());
        jobListing.setDescription(request.getDescription());
        jobListing.setAiReviewEnabled(Boolean.TRUE.equals(request.getAiReviewEnabled()));
        jobListing.setAiReviewMinScore(request.getAiReviewMinScore());
        jobListing.setStatus(JobListingMapper.INSTANCE.toDomain(request.getStatus()));
        jobListing = jobListingRepository.saveAndFlush(jobListing);
        log.debug("Updated job listing {} by user {}", jobListingId, userId);
        return JobListingMapper.INSTANCE.toModel(jobListing);
    }

    @Override
    @Transactional
    public void deleteJobListing(Long jobListingId) {
        String userId = securityService.getId();
        log.debug("Deleting job listing {} by user {}", jobListingId, userId);
        JobListing jobListing = getJobListingForManager(jobListingId, userId);
        jobListingRepository.delete(jobListing);
        log.debug("Deleted job listing {} by user {}", jobListingId, userId);
    }

    private JobListing getJobListingForManager(Long jobListingId, String userId) {
        JobListing jobListing = getJobListing(jobListingId);
        if (!companySecurityService.hasPermission(
                jobListing.getCompany().getCompanyId(), CompanyPermission.MANAGE_JOB_LISTINGS)) {
            log.debug("User {} is not an authorized manager for company {} of job listing {}",
                    userId, jobListing.getCompany().getCompanyId(), jobListingId);
            throw new ResourceNotFoundException(JOB_LISTING_NOT_FOUND_MESSAGE.formatted(jobListingId));
        }
        return jobListing;
    }

    private @NonNull JobListing getJobListing(Long jobListingId) {
        return jobListingRepository.findById(jobListingId)
                .orElseThrow(() -> {
                    log.debug("No job listing with id {}", jobListingId);
                    return new ResourceNotFoundException(JOB_LISTING_NOT_FOUND_MESSAGE.formatted(jobListingId));
                });
    }

}
