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

import hu.robottx.ats.domain.ApplicationStatus;
import hu.robottx.ats.domain.CompanyPermission;
import hu.robottx.ats.domain.EmployeeRole;
import hu.robottx.ats.domain.EmployeeStatus;
import hu.robottx.ats.domain.FeedbackType;
import hu.robottx.ats.entity.Application;
import hu.robottx.ats.entity.Employee;
import hu.robottx.ats.entity.Feedback;
import hu.robottx.ats.exception.ConflictException;
import hu.robottx.ats.exception.ResourceNotFoundException;
import hu.robottx.ats.mapper.FeedbackMapper;
import hu.robottx.ats.model.FeedbackResponse;
import hu.robottx.ats.model.FeedbackSaveRequest;
import hu.robottx.ats.model.FeedbackUpdateRequest;
import hu.robottx.ats.model.PagedFeedbackResponse;
import hu.robottx.ats.model.SearchAndPageRequest;
import hu.robottx.ats.repository.ApplicationRepository;
import hu.robottx.ats.repository.EmployeeRepository;
import hu.robottx.ats.repository.FeedbackRepository;
import hu.robottx.ats.security.service.SecurityService;
import hu.robottx.ats.util.FeedbackQueryParamsUtil;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private static final String APPLICATION_NOT_FOUND_MESSAGE = "No application with id: %d";
    private static final String FEEDBACK_NOT_FOUND_MESSAGE = "No feedback with id: %d";
    private static final Set<ApplicationStatus> TECHNICAL_STAGE_STATUSES = EnumSet.of(
            ApplicationStatus.TECHNICAL_INTERVIEW_SCHEDULED,
            ApplicationStatus.TECHNICAL_INTERVIEWED
    );

    private final SecurityService securityService;
    private final ApplicationRepository applicationRepository;
    private final EmployeeRepository employeeRepository;
    private final FeedbackRepository feedbackRepository;

    @Override
    @Transactional(readOnly = true)
    public PagedFeedbackResponse getApplicationFeedbacks(Long applicationId, SearchAndPageRequest request) {
        String userId = securityService.getId();
        log.debug("Getting feedbacks for application {} by user {} with request {}",
                applicationId, userId, request);
        Application application = getApplication(applicationId);
        Employee employee = getAuthorizedEmployeeForViewing(application, userId);
        FeedbackType restrictedType = resolveRestrictedFeedbackType(employee);
        PageRequest pageRequest = PageRequest.of(request.getPageNumber(), request.getPageSize());
        Specification<Feedback> spec =
                FeedbackQueryParamsUtil.createSearchSpecification(applicationId, restrictedType, request);
        try {
            Page<Feedback> feedbacks = feedbackRepository.findAll(spec, pageRequest);
            return FeedbackMapper.INSTANCE.toModel(feedbacks);
        } catch (RuntimeException ex) {
            log.debug("Invalid query or sort parameters for application {}: query='{}', sort='{}'",
                    applicationId, request.getQuery(), request.getSort());
            throw new IllegalArgumentException("Invalid query or sort parameters", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackResponse getApplicationFeedbackById(Long applicationId, Long feedbackId) {
        String userId = securityService.getId();
        log.debug("Getting feedback {} for application {} by user {}", feedbackId, applicationId, userId);
        Application application = getApplication(applicationId);
        Employee employee = getAuthorizedEmployeeForViewing(application, userId);
        Feedback feedback = getFeedbackForViewing(applicationId, feedbackId, employee);
        return FeedbackMapper.INSTANCE.toModel(feedback);
    }

    @Override
    @Transactional
    public FeedbackResponse saveApplicationFeedback(Long applicationId, FeedbackSaveRequest request) {
        String userId = securityService.getId();
        log.debug("Creating feedback for application {} by user {}", applicationId, userId);
        Application application = getApplication(applicationId);
        Employee employee = getAuthorizedEmployeeForViewing(application, userId);
        FeedbackType feedbackType = resolveFeedbackTypeForCreation(application, employee);
        if (feedbackRepository.existsByApplicationApplicationIdAndEmployeeEmployeeIdAndType(
                applicationId, employee.getEmployeeId(), feedbackType)) {
            log.debug("Employee {} has already submitted {} feedback for application {}",
                    employee.getEmployeeId(), feedbackType, applicationId);
            throw new ConflictException(
                    "Employee has already submitted %s feedback for application with id: %d"
                            .formatted(feedbackType, applicationId));
        }
        Feedback feedback = Feedback.builder()
                .application(application)
                .employee(employee)
                .type(feedbackType)
                .feedback(request.getFeedback())
                .score(request.getScore())
                .build();
        feedback = feedbackRepository.save(feedback);
        log.debug("Saved feedback {} for application {} by employee {}",
                feedback.getFeedbackId(), applicationId, employee.getEmployeeId());
        return FeedbackMapper.INSTANCE.toModel(feedback);
    }

    @Override
    @Transactional
    public FeedbackResponse updateApplicationFeedback(
            Long applicationId, Long feedbackId, FeedbackUpdateRequest request) {
        String userId = securityService.getId();
        log.debug("Updating feedback {} for application {} by user {}", feedbackId, applicationId, userId);
        Application application = getApplication(applicationId);
        Employee employee = getAuthorizedEmployeeForViewing(application, userId);
        Feedback feedback = getFeedbackForViewing(applicationId, feedbackId, employee);
        validateAuthorAndStageForModification(application, feedback, employee);
        feedback.setFeedback(request.getFeedback());
        feedback.setScore(request.getScore());
        feedback = feedbackRepository.saveAndFlush(feedback);
        log.debug("Updated feedback {} for application {} by employee {}",
                feedbackId, applicationId, employee.getEmployeeId());
        return FeedbackMapper.INSTANCE.toModel(feedback);
    }

    @Override
    @Transactional
    public void deleteApplicationFeedback(Long applicationId, Long feedbackId) {
        String userId = securityService.getId();
        log.debug("Deleting feedback {} for application {} by user {}", feedbackId, applicationId, userId);
        Application application = getApplication(applicationId);
        Employee employee = getAuthorizedEmployeeForViewing(application, userId);
        Feedback feedback = getFeedbackForViewing(applicationId, feedbackId, employee);
        validateAuthorAndStageForModification(application, feedback, employee);
        feedbackRepository.delete(feedback);
        log.debug("Deleted feedback {} for application {} by employee {}",
                feedbackId, applicationId, employee.getEmployeeId());
    }

    private Application getApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> {
                    log.debug("No application with id {}", applicationId);
                    return new ResourceNotFoundException(APPLICATION_NOT_FOUND_MESSAGE.formatted(applicationId));
                });
    }

    private Employee getAuthorizedEmployeeForViewing(Application application, String userId) {
        Long companyId = application.getJobListing().getCompany().getCompanyId();
        Employee employee = employeeRepository
                .findByCompanyCompanyIdAndUserUserIdAndStatus(companyId, userId, EmployeeStatus.ACTIVE)
                .orElseThrow(() -> {
                    log.debug("User {} is not an active employee in company {}", userId, companyId);
                    return new ResourceNotFoundException(
                            APPLICATION_NOT_FOUND_MESSAGE.formatted(application.getApplicationId()));
                });
        EmployeeRole role = employee.getRole();
        if (!role.hasPermission(CompanyPermission.VIEW_ALL_APPLICATIONS)
                && (!role.hasPermission(CompanyPermission.VIEW_TECHNICAL_STAGE_APPLICATIONS)
                || !TECHNICAL_STAGE_STATUSES.contains(application.getStatus()))) {
            log.debug("User {} with role {} cannot access application {} in status {}",
                    userId, role, application.getApplicationId(), application.getStatus());
            throw new ResourceNotFoundException(
                    APPLICATION_NOT_FOUND_MESSAGE.formatted(application.getApplicationId()));
        }
        return employee;
    }

    private FeedbackType resolveRestrictedFeedbackType(Employee employee) {
        if (employee.getRole().hasPermission(CompanyPermission.VIEW_ALL_APPLICATIONS)) {
            return null;
        }
        return FeedbackType.TECHNICAL;
    }

    private Feedback getFeedbackForViewing(Long applicationId, Long feedbackId, Employee employee) {
        Feedback feedback = feedbackRepository
                .findByFeedbackIdAndApplicationApplicationId(feedbackId, applicationId)
                .orElseThrow(() -> {
                    log.debug("No feedback {} found for application {}", feedbackId, applicationId);
                    return new ResourceNotFoundException(FEEDBACK_NOT_FOUND_MESSAGE.formatted(feedbackId));
                });
        FeedbackType restrictedType = resolveRestrictedFeedbackType(employee);
        if (restrictedType != null && feedback.getType() != restrictedType) {
            log.debug("Employee {} cannot view feedback {} of type {}",
                    employee.getEmployeeId(), feedbackId, feedback.getType());
            throw new ResourceNotFoundException(FEEDBACK_NOT_FOUND_MESSAGE.formatted(feedbackId));
        }
        return feedback;
    }

    private FeedbackType resolveFeedbackTypeForCreation(Application application, Employee employee) {
        ApplicationStatus status = application.getStatus();
        EmployeeRole role = employee.getRole();
        if (status == ApplicationStatus.HR_INTERVIEWED) {
            if (!role.hasPermission(CompanyPermission.CREATE_HR_FEEDBACK)) {
                throw new IllegalArgumentException("User does not have permission to create HR feedback");
            }
            return FeedbackType.HR;
        }
        if (status == ApplicationStatus.TECHNICAL_INTERVIEWED) {
            if (!role.hasPermission(CompanyPermission.CREATE_TECHNICAL_FEEDBACK)) {
                throw new IllegalArgumentException("User does not have permission to create technical feedback");
            }
            return FeedbackType.TECHNICAL;
        }
        throw new IllegalArgumentException(
                "Feedback can only be added when application is in HR_INTERVIEWED or TECHNICAL_INTERVIEWED status");
    }

    private void validateAuthorAndStageForModification(
            Application application, Feedback feedback, Employee employee) {
        if (!feedback.getEmployee().getEmployeeId().equals(employee.getEmployeeId())) {
            throw new IllegalArgumentException("Only the author can modify or delete this feedback");
        }
        ApplicationStatus expectedStatus = feedback.getType() == FeedbackType.HR
                                           ? ApplicationStatus.HR_INTERVIEWED
                                           : ApplicationStatus.TECHNICAL_INTERVIEWED;
        if (application.getStatus() != expectedStatus) {
            throw new IllegalArgumentException(
                    "Feedback can only be modified or deleted while the application is in status: " + expectedStatus);
        }
    }

}
