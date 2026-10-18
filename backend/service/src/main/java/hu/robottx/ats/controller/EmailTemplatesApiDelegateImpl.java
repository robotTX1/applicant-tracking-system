package hu.robottx.ats.controller;

import static hu.robottx.ats.controller.RestControllerConstants.EMAIL_TEMPLATE_BY_ID_ENDPOINT;

import java.net.URI;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import hu.robottx.ats.api.EmailTemplatesApiDelegate;
import hu.robottx.ats.model.EmailTemplateResponse;
import hu.robottx.ats.model.EmailTemplateSaveRequest;
import hu.robottx.ats.model.EmailTemplateUpdateRequest;
import hu.robottx.ats.model.PagedEmailTemplateResponse;
import hu.robottx.ats.model.SearchAndPageRequest;
import hu.robottx.ats.service.EmailTemplateService;

@Component
@RequiredArgsConstructor
public class EmailTemplatesApiDelegateImpl implements EmailTemplatesApiDelegate {

    private final EmailTemplateService emailTemplateService;

    @Override
    public ResponseEntity<PagedEmailTemplateResponse> getEmailTemplates(Long companyId,
            SearchAndPageRequest searchAndPageRequest) {
        PagedEmailTemplateResponse response = emailTemplateService.getEmailTemplates(companyId, searchAndPageRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<EmailTemplateResponse> getEmailTemplate(Long companyId, Long emailTemplateId) {
        EmailTemplateResponse response = emailTemplateService.getEmailTemplateById(companyId, emailTemplateId);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<EmailTemplateResponse> saveEmailTemplate(Long companyId,
            EmailTemplateSaveRequest emailTemplateSaveRequest) {
        EmailTemplateResponse response = emailTemplateService.saveEmailTemplate(companyId, emailTemplateSaveRequest);
        return ResponseEntity.created(buildUri(companyId, response.getId())).body(response);
    }

    @Override
    public ResponseEntity<EmailTemplateResponse> updateEmailTemplate(Long companyId, Long emailTemplateId,
            EmailTemplateUpdateRequest emailTemplateUpdateRequest) {
        EmailTemplateResponse response = emailTemplateService.updateEmailTemplate(
                companyId, emailTemplateId, emailTemplateUpdateRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteEmailTemplate(Long companyId, Long emailTemplateId) {
        emailTemplateService.deleteEmailTemplate(companyId, emailTemplateId);
        return ResponseEntity.noContent().build();
    }

    private URI buildUri(Long companyId, Long emailTemplateId) {
        return UriComponentsBuilder.fromUriString(EMAIL_TEMPLATE_BY_ID_ENDPOINT)
                .buildAndExpand(companyId, emailTemplateId)
                .toUri();
    }

}
