package hu.robottx.ats.service;

import java.util.EnumSet;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import hu.robottx.ats.domain.ApplicationStatus;
import hu.robottx.ats.domain.CompanyPermission;
import hu.robottx.ats.domain.DocumentDownload;
import hu.robottx.ats.domain.JobStatus;
import hu.robottx.ats.entity.Application;
import hu.robottx.ats.entity.Document;
import hu.robottx.ats.entity.JobListing;
import hu.robottx.ats.entity.User;
import hu.robottx.ats.exception.ConflictException;
import hu.robottx.ats.exception.ResourceNotFoundException;
import hu.robottx.ats.mapper.ApplicationMapper;
import hu.robottx.ats.mapper.DocumentMapper;
import hu.robottx.ats.model.ApplicationSaveRequest;
import hu.robottx.ats.model.DocumentResponse;
import hu.robottx.ats.model.JobApplicationResponse;
import hu.robottx.ats.model.PagedJobApplicationResponse;
import hu.robottx.ats.model.PagedUserApplicationResponse;
import hu.robottx.ats.model.SearchAndPageRequest;
import hu.robottx.ats.model.UserApplicationResponse;
import hu.robottx.ats.repository.ApplicationRepository;
import hu.robottx.ats.repository.JobListingRepository;
import hu.robottx.ats.repository.UserRepository;
import hu.robottx.ats.security.service.CompanySecurityService;
import hu.robottx.ats.security.service.SecurityService;
import hu.robottx.ats.util.ApplicationQueryParamsUtil;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    private static final String APPLICATION_CV_KEY_PREFIX = "applications";
    private static final String JOB_LISTING_NOT_FOUND_MESSAGE = "No job listing with id: %d";
    private static final String APPLICATION_NOT_FOUND_MESSAGE = "No application with id: %d";
    private static final Set<ApplicationStatus> TECHNICAL_STAGE_STATUSES = EnumSet.of(
            ApplicationStatus.TECHNICAL_INTERVIEW_SCHEDULED,
            ApplicationStatus.TECHNICAL_INTERVIEWED
    );

    private final SecurityService securityService;
    private final CompanySecurityService companySecurityService;
    private final UserRepository userRepository;
    private final JobListingRepository jobListingRepository;
    private final ApplicationRepository applicationRepository;
    private final DocumentService documentService;

    @Override
    @Transactional(readOnly = true)
    public PagedUserApplicationResponse getCurrentUserApplications(SearchAndPageRequest request) {
        String userId = securityService.getId();
        log.debug("Getting applications for current user {} with request {}", userId, request);
        PageRequest pageRequest = PageRequest.of(request.getPageNumber(), request.getPageSize());
        Specification<Application> spec = ApplicationQueryParamsUtil.createUserSearchSpecification(userId, request);
        try {
            Page<Application> applications = applicationRepository.findAll(spec, pageRequest);
            return ApplicationMapper.INSTANCE.toUserApplicationPageModel(applications);
        } catch (RuntimeException ex) {
            log.debug("Invalid query or sort parameters: query='{}', sort='{}'", request.getQuery(), request.getSort());
            throw new IllegalArgumentException("Invalid query or sort parameters", ex);
        }
    }

    @Override
    @Transactional
    public UserApplicationResponse saveCurrentUserApplication(ApplicationSaveRequest request, MultipartFile file) {
        String userId = securityService.getId();
        Long jobListingId = request.getJobListingId();
        log.debug("Creating application for job listing {} by user {}", jobListingId, userId);
        JobListing jobListing = getJobListing(jobListingId);
        if (jobListing.getStatus() != JobStatus.PUBLISHED) {
            log.debug("Job listing {} is not published (status={})", jobListingId, jobListing.getStatus());
            throw new ResourceNotFoundException(JOB_LISTING_NOT_FOUND_MESSAGE.formatted(jobListingId));
        }
        Long companyId = jobListing.getCompany().getCompanyId();
        if (companySecurityService.hasPermission(companyId, CompanyPermission.VIEW_EMPLOYEES)) {
            log.debug("User {} is an active employee of company {} and cannot apply to job listing {}",
                    userId, companyId, jobListingId);
            throw new IllegalArgumentException("Employees cannot apply to job listings of their own company");
        }
        if (applicationRepository.existsByJobListingJobListingIdAndUserUserId(jobListingId, userId)) {
            log.debug("User {} has already applied to job listing {}", userId, jobListingId);
            throw new ConflictException("User has already applied to job listing with id: %d".formatted(jobListingId));
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        Document cvDocument = resolveApplicationCvDocument(user, file);
        Application application = Application.builder()
                .jobListing(jobListing)
                .user(user)
                .cvDocument(cvDocument)
                .applicantMessage(request.getApplicantMessage())
                .status(ApplicationStatus.APPLIED)
                .build();
        application = applicationRepository.save(application);
        log.debug("Created application with id {} for job listing {} by user {}",
                application.getApplicationId(), jobListingId, userId);
        return ApplicationMapper.INSTANCE.toUserApplicationModel(application);
    }

    @Override
    @Transactional(readOnly = true)
    public UserApplicationResponse getCurrentUserApplicationById(Long applicationId) {
        String userId = securityService.getId();
        log.debug("Getting application {} for current user {}", applicationId, userId);
        Application application = getApplicationForCurrentUser(applicationId, userId);
        return ApplicationMapper.INSTANCE.toUserApplicationModel(application);
    }

    @Override
    @Transactional
    public void deleteCurrentUserApplication(Long applicationId) {
        String userId = securityService.getId();
        log.debug("Deleting application {} for current user {}", applicationId, userId);
        Application application = getApplicationForCurrentUser(applicationId, userId);
        Document cvDocument = application.getCvDocument();
        applicationRepository.delete(application);
        documentService.deleteDocument(cvDocument);
        log.debug("Deleted application {} and its CV document for user {}", applicationId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDownload getCurrentUserApplicationCv(Long applicationId) {
        String userId = securityService.getId();
        log.debug("Downloading CV for application {} by current user {}", applicationId, userId);
        Application application = getApplicationForCurrentUser(applicationId, userId);
        return documentService.downloadDocument(application.getCvDocument());
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getCurrentUserApplicationCvMetadata(Long applicationId) {
        String userId = securityService.getId();
        log.debug("Getting CV metadata for application {} by current user {}", applicationId, userId);
        Application application = getApplicationForCurrentUser(applicationId, userId);
        return DocumentMapper.INSTANCE.toModel(application.getCvDocument());
    }

    @Override
    @Transactional(readOnly = true)
    public PagedJobApplicationResponse getJobListingApplications(Long jobListingId, SearchAndPageRequest request) {
        String userId = securityService.getId();
        log.debug("Getting applications for job listing {} by user {} with request {}",
                jobListingId, userId, request);
        Set<ApplicationStatus> allowedStatuses = resolveAllowedStatusesForJobListing(jobListingId, userId);
        PageRequest pageRequest = PageRequest.of(request.getPageNumber(), request.getPageSize());
        Specification<Application> spec = ApplicationQueryParamsUtil.createJobListingSearchSpecification(
                jobListingId, allowedStatuses, request);
        try {
            Page<Application> applications = applicationRepository.findAll(spec, pageRequest);
            return ApplicationMapper.INSTANCE.toJobApplicationPageModel(applications);
        } catch (RuntimeException ex) {
            log.debug("Invalid query or sort parameters: query='{}', sort='{}'", request.getQuery(), request.getSort());
            throw new IllegalArgumentException("Invalid query or sort parameters", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public JobApplicationResponse getJobListingApplicationById(Long jobListingId, Long applicationId) {
        String userId = securityService.getId();
        log.debug("Getting application {} for job listing {} by user {}", applicationId, jobListingId, userId);
        Application application = getApplicationForJobListing(jobListingId, applicationId, userId);
        return ApplicationMapper.INSTANCE.toJobApplicationModel(application);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDownload getJobListingApplicationCv(Long jobListingId, Long applicationId) {
        String userId = securityService.getId();
        log.debug("Downloading CV for application {} of job listing {} by user {}",
                applicationId, jobListingId, userId);
        Application application = getApplicationForJobListing(jobListingId, applicationId, userId);
        return documentService.downloadDocument(application.getCvDocument());
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getJobListingApplicationCvMetadata(Long jobListingId, Long applicationId) {
        String userId = securityService.getId();
        log.debug("Getting CV metadata for application {} of job listing {} by user {}",
                applicationId, jobListingId, userId);
        Application application = getApplicationForJobListing(jobListingId, applicationId, userId);
        return DocumentMapper.INSTANCE.toModel(application.getCvDocument());
    }

    private Document resolveApplicationCvDocument(User user, MultipartFile file) {
        if (file != null) {
            return documentService.uploadCvDocument(APPLICATION_CV_KEY_PREFIX, user.getUserId(), file);
        }
        Document defaultCv = user.getCvDocument();
        if (defaultCv == null) {
            throw new IllegalArgumentException("No CV file provided and no default CV found for user");
        }
        return documentService.copyCvDocument(APPLICATION_CV_KEY_PREFIX, user.getUserId(), defaultCv);
    }

    private Application getApplicationForCurrentUser(Long applicationId, String userId) {
        return applicationRepository.findByApplicationIdAndUserUserId(applicationId, userId)
                .orElseThrow(() -> {
                    log.debug("No application {} found for user {}", applicationId, userId);
                    return new ResourceNotFoundException(APPLICATION_NOT_FOUND_MESSAGE.formatted(applicationId));
                });
    }

    private Set<ApplicationStatus> resolveAllowedStatusesForJobListing(Long jobListingId, String userId) {
        JobListing jobListing = getJobListing(jobListingId);
        Long companyId = jobListing.getCompany().getCompanyId();
        if (companySecurityService.hasPermission(companyId, userId, CompanyPermission.VIEW_ALL_APPLICATIONS)) {
            return null;
        }
        if (companySecurityService.hasPermission(
                companyId, userId, CompanyPermission.VIEW_TECHNICAL_STAGE_APPLICATIONS)) {
            return TECHNICAL_STAGE_STATUSES;
        }
        log.debug("User {} does not have permission to view applications for job listing {} (company {})",
                userId, jobListingId, companyId);
        throw new ResourceNotFoundException(JOB_LISTING_NOT_FOUND_MESSAGE.formatted(jobListingId));
    }

    private JobListing getJobListing(Long jobListingId) {
        return jobListingRepository.findById(jobListingId)
                .orElseThrow(() -> {
                    log.debug("No job listing with id {}", jobListingId);
                    return new ResourceNotFoundException(JOB_LISTING_NOT_FOUND_MESSAGE.formatted(jobListingId));
                });
    }

    private Application getApplicationForJobListing(Long jobListingId, Long applicationId, String userId) {
        Set<ApplicationStatus> allowedStatuses = resolveAllowedStatusesForJobListing(jobListingId, userId);
        Application application = applicationRepository
                .findByApplicationIdAndJobListingJobListingId(applicationId, jobListingId)
                .orElseThrow(() -> {
                    log.debug("No application {} found for job listing {}", applicationId, jobListingId);
                    return new ResourceNotFoundException(APPLICATION_NOT_FOUND_MESSAGE.formatted(applicationId));
                });
        if (allowedStatuses != null && !allowedStatuses.contains(application.getStatus())) {
            log.debug("User {} cannot access application {} in status {}",
                    userId, applicationId, application.getStatus());
            throw new ResourceNotFoundException(APPLICATION_NOT_FOUND_MESSAGE.formatted(applicationId));
        }
        return application;
    }

}
