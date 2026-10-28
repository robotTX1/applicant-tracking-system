package hu.robottx.ats.service;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hu.robottx.ats.UnitTest;
import hu.robottx.ats.domain.EmailTemplateType;
import hu.robottx.ats.entity.Company;
import hu.robottx.ats.entity.EmailTemplate;
import hu.robottx.ats.provider.DefaultEmailTemplateProvider;
import hu.robottx.ats.repository.CompanyRepository;
import hu.robottx.ats.repository.EmailTemplateRepository;
import hu.robottx.ats.repository.JobListingRepository;
import hu.robottx.ats.security.service.CompanySecurityService;
import hu.robottx.ats.security.service.SecurityService;

class EmailTemplateServiceImplTest extends UnitTest {

    @Mock
    private SecurityService securityService;

    @Mock
    private CompanySecurityService companySecurityService;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private EmailTemplateRepository emailTemplateRepository;

    @Mock
    private JobListingRepository jobListingRepository;

    @Mock
    private DefaultEmailTemplateProvider defaultEmailTemplateProvider;

    @InjectMocks
    private EmailTemplateServiceImpl emailTemplateService;

    @Test
    @DisplayName("createDefaultEmailTemplates saves default OFFER and DECLINE templates for company")
    void createDefaultEmailTemplates() {
        when(defaultEmailTemplateProvider.getOfferSubject()).thenReturn("Offer Subject");
        when(defaultEmailTemplateProvider.getOfferBody()).thenReturn("Offer Body");
        when(defaultEmailTemplateProvider.getDeclineSubject()).thenReturn("Decline Subject");
        when(defaultEmailTemplateProvider.getDeclineBody()).thenReturn("Decline Body");
        Company company = Company.builder()
                .companyId(42L)
                .name("Acme Corp")
                .build();
        emailTemplateService.createDefaultEmailTemplates(company);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EmailTemplate>> captor = ArgumentCaptor.forClass(List.class);
        verify(emailTemplateRepository).saveAll(captor.capture());
        List<EmailTemplate> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        EmailTemplate offer = saved.stream()
                .filter(t -> t.getType() == EmailTemplateType.OFFER)
                .findFirst()
                .orElseThrow();
        assertThat(offer.getCompany()).isEqualTo(company);
        assertThat(offer.getSubject()).isEqualTo("Offer Subject");
        assertThat(offer.getBody()).isEqualTo("Offer Body");
        EmailTemplate decline = saved.stream()
                .filter(t -> t.getType() == EmailTemplateType.DECLINE)
                .findFirst()
                .orElseThrow();
        assertThat(decline.getCompany()).isEqualTo(company);
        assertThat(decline.getSubject()).isEqualTo("Decline Subject");
        assertThat(decline.getBody()).isEqualTo("Decline Body");
    }

}
