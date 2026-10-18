package hu.robottx.ats.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hu.robottx.ats.domain.CompanyPermission;
import hu.robottx.ats.domain.EmailTemplateType;
import hu.robottx.ats.entity.Company;
import hu.robottx.ats.entity.EmailTemplate;
import hu.robottx.ats.exception.ResourceLimitException;
import hu.robottx.ats.exception.ResourceNotFoundException;
import hu.robottx.ats.mapper.EmailTemplateMapper;
import hu.robottx.ats.model.EmailTemplateResponse;
import hu.robottx.ats.model.EmailTemplateSaveRequest;
import hu.robottx.ats.model.EmailTemplateUpdateRequest;
import hu.robottx.ats.model.PagedEmailTemplateResponse;
import hu.robottx.ats.model.SearchAndPageRequest;
import hu.robottx.ats.repository.CompanyRepository;
import hu.robottx.ats.repository.EmailTemplateRepository;
import hu.robottx.ats.security.service.CompanySecurityService;
import hu.robottx.ats.security.service.SecurityService;
import hu.robottx.ats.util.EmailTemplateQueryParamsUtil;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailTemplateServiceImpl implements EmailTemplateService {

    private static final int MAX_TEMPLATES_PER_TYPE = 3;
    private static final String EMAIL_TEMPLATE_NOT_FOUND_MESSAGE = "No email template with id: %d";
    private static final String TEMPLATE_LIMIT_MESSAGE =
            "Maximum limit of %d email templates reached for type %s";

    private final SecurityService securityService;
    private final CompanySecurityService companySecurityService;
    private final CompanyRepository companyRepository;
    private final EmailTemplateRepository emailTemplateRepository;

    @Override
    @Transactional(readOnly = true)
    public PagedEmailTemplateResponse getEmailTemplates(Long companyId, SearchAndPageRequest request) {
        String userId = securityService.getId();
        log.debug("Getting email templates for company {} by user {}", companyId, userId);
        companySecurityService.validatePermission(companyId, CompanyPermission.MANAGE_EMAIL_TEMPLATES);
        PageRequest pageRequest = PageRequest.of(request.getPageNumber(), request.getPageSize());
        Specification<EmailTemplate> spec = EmailTemplateQueryParamsUtil.createSearchSpecification(companyId, request);
        try {
            Page<EmailTemplate> emailTemplates = emailTemplateRepository.findAll(spec, pageRequest);
            return EmailTemplateMapper.INSTANCE.toModel(emailTemplates);
        } catch (RuntimeException ex) {
            log.debug("Invalid query or sort parameters for company {}: query='{}', sort='{}'",
                    companyId, request.getQuery(), request.getSort());
            throw new IllegalArgumentException("Invalid query or sort parameters", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public EmailTemplateResponse getEmailTemplateById(Long companyId, Long emailTemplateId) {
        String userId = securityService.getId();
        log.debug("Getting email template {} for company {} by user {}", emailTemplateId, companyId, userId);
        companySecurityService.validatePermission(companyId, CompanyPermission.MANAGE_EMAIL_TEMPLATES);
        EmailTemplate emailTemplate = getEmailTemplate(companyId, emailTemplateId);
        return EmailTemplateMapper.INSTANCE.toModel(emailTemplate);
    }

    @Override
    @Transactional
    public EmailTemplateResponse saveEmailTemplate(Long companyId, EmailTemplateSaveRequest request) {
        String userId = securityService.getId();
        log.debug("Creating email template {} for company {} by user {}", request, companyId, userId);
        companySecurityService.validatePermission(companyId, CompanyPermission.MANAGE_EMAIL_TEMPLATES);
        EmailTemplateType domainType = EmailTemplateMapper.INSTANCE.toDomain(request.getType());
        long count = emailTemplateRepository.countByCompanyCompanyIdAndType(companyId, domainType);
        if (count >= MAX_TEMPLATES_PER_TYPE) {
            log.debug("Company {} has reached maximum templates for type {}", companyId, domainType);
            throw new ResourceLimitException(TEMPLATE_LIMIT_MESSAGE.formatted(MAX_TEMPLATES_PER_TYPE, domainType));
        }
        Company company = companyRepository.getReferenceById(companyId);
        EmailTemplate emailTemplate = EmailTemplate.builder()
                .company(company)
                .type(domainType)
                .subject(request.getSubject())
                .body(request.getBody())
                .build();
        emailTemplate = emailTemplateRepository.save(emailTemplate);
        log.debug("Saved email template with id {} for company {}", emailTemplate.getEmailTemplateId(), companyId);
        return EmailTemplateMapper.INSTANCE.toModel(emailTemplate);
    }

    @Override
    @Transactional
    public EmailTemplateResponse updateEmailTemplate(Long companyId, Long emailTemplateId,
            EmailTemplateUpdateRequest request) {
        String userId = securityService.getId();
        log.debug("Updating email template {} for company {} by user {}", emailTemplateId, companyId, userId);
        companySecurityService.validatePermission(companyId, CompanyPermission.MANAGE_EMAIL_TEMPLATES);
        EmailTemplate emailTemplate = getEmailTemplate(companyId, emailTemplateId);
        EmailTemplateType newType = EmailTemplateMapper.INSTANCE.toDomain(request.getType());
        if (emailTemplate.getType() != newType) {
            long count = emailTemplateRepository.countByCompanyCompanyIdAndType(companyId, newType);
            if (count >= MAX_TEMPLATES_PER_TYPE) {
                log.debug("Company {} has reached maximum templates for type {}", companyId, newType);
                throw new ResourceLimitException(TEMPLATE_LIMIT_MESSAGE.formatted(MAX_TEMPLATES_PER_TYPE, newType));
            }
        }
        emailTemplate.setType(newType);
        emailTemplate.setSubject(request.getSubject());
        emailTemplate.setBody(request.getBody());
        emailTemplate = emailTemplateRepository.saveAndFlush(emailTemplate);
        log.debug("Updated email template {} for company {}", emailTemplateId, companyId);
        return EmailTemplateMapper.INSTANCE.toModel(emailTemplate);
    }

    @Override
    @Transactional
    public void deleteEmailTemplate(Long companyId, Long emailTemplateId) {
        String userId = securityService.getId();
        log.debug("Deleting email template {} for company {} by user {}", emailTemplateId, companyId, userId);
        companySecurityService.validatePermission(companyId, CompanyPermission.MANAGE_EMAIL_TEMPLATES);
        EmailTemplate emailTemplate = getEmailTemplate(companyId, emailTemplateId);
        emailTemplateRepository.delete(emailTemplate);
        log.debug("Deleted email template {} for company {}", emailTemplateId, companyId);
    }

    private EmailTemplate getEmailTemplate(Long companyId, Long emailTemplateId) {
        return emailTemplateRepository.findByEmailTemplateIdAndCompanyCompanyId(emailTemplateId, companyId)
                .orElseThrow(() -> {
                    log.debug("No email template with id {} in company {}", emailTemplateId, companyId);
                    return new ResourceNotFoundException(
                            EMAIL_TEMPLATE_NOT_FOUND_MESSAGE.formatted(emailTemplateId));
                });
    }

}
