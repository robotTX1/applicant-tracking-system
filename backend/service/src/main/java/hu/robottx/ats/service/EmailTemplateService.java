package hu.robottx.ats.service;

import hu.robottx.ats.model.EmailTemplateResponse;
import hu.robottx.ats.model.EmailTemplateSaveRequest;
import hu.robottx.ats.model.EmailTemplateUpdateRequest;
import hu.robottx.ats.model.PagedEmailTemplateResponse;
import hu.robottx.ats.model.SearchAndPageRequest;

public interface EmailTemplateService {

    PagedEmailTemplateResponse getEmailTemplates(Long companyId, SearchAndPageRequest request);

    EmailTemplateResponse getEmailTemplateById(Long companyId, Long emailTemplateId);

    EmailTemplateResponse saveEmailTemplate(Long companyId, EmailTemplateSaveRequest request);

    EmailTemplateResponse updateEmailTemplate(Long companyId, Long emailTemplateId,
            EmailTemplateUpdateRequest request);

    void deleteEmailTemplate(Long companyId, Long emailTemplateId);

}
