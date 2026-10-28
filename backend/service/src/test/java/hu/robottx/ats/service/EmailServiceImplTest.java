package hu.robottx.ats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import hu.robottx.ats.UnitTest;
import hu.robottx.ats.config.MailConfig;
import hu.robottx.ats.domain.ApplicationStatus;
import hu.robottx.ats.domain.EmailTemplateType;
import hu.robottx.ats.entity.Application;
import hu.robottx.ats.entity.Company;
import hu.robottx.ats.entity.EmailTemplate;
import hu.robottx.ats.entity.JobListing;
import hu.robottx.ats.entity.User;
import hu.robottx.ats.event.ApplicationDecisionEmailEvent;

class EmailServiceImplTest extends UnitTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MailConfig mailConfig;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private EmailServiceImpl emailService;

    @Test
    @DisplayName("sendApplicationDecisionEmail publishes event with substituted offer email when status is OFFER")
    void sendOfferEmail() {
        Application application = createApplication(ApplicationStatus.OFFER);
        emailService.sendApplicationDecisionEmail(application);
        ArgumentCaptor<ApplicationDecisionEmailEvent> captor =
                ArgumentCaptor.forClass(ApplicationDecisionEmailEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ApplicationDecisionEmailEvent event = captor.getValue();
        assertThat(event.applicationId()).isEqualTo(10L);
        assertThat(event.recipientEmail()).isEqualTo("jane.doe@example.com");
        assertThat(event.subject()).isEqualTo("Offer for Senior Java Developer at TechCorp");
        assertThat(event.body()).isEqualTo(
                "Dear Jane Doe, we are pleased to offer you the Senior Java Developer role in Budapest at TechCorp.");
    }

    @Test
    @DisplayName("sendApplicationDecisionEmail publishes event with substituted decline email when status is DECLINED")
    void sendDeclineEmail() {
        Application application = createApplication(ApplicationStatus.DECLINED);
        emailService.sendApplicationDecisionEmail(application);
        ArgumentCaptor<ApplicationDecisionEmailEvent> captor =
                ArgumentCaptor.forClass(ApplicationDecisionEmailEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ApplicationDecisionEmailEvent event = captor.getValue();
        assertThat(event.applicationId()).isEqualTo(10L);
        assertThat(event.recipientEmail()).isEqualTo("jane.doe@example.com");
        assertThat(event.subject()).isEqualTo("Update on your Senior Java Developer application at TechCorp");
        assertThat(event.body()).isEqualTo(
                "Dear Jane Doe, thank you for applying to Senior Java Developer in Budapest at TechCorp.");
    }

    @Test
    @DisplayName("sendApplicationDecisionEmail does nothing for non-decision statuses")
    void ignoreNonDecisionStatus() {
        Application application = createApplication(ApplicationStatus.HR_INTERVIEW_SCHEDULED);
        emailService.sendApplicationDecisionEmail(application);
        verifyNoInteractions(eventPublisher, mailSender);
    }

    @Test
    @DisplayName("handleApplicationDecisionEmail sends mail message successfully")
    void handleApplicationDecisionEmailSuccess() {
        when(mailConfig.getFrom()).thenReturn("noreply@ats.robottx.hu");
        ApplicationDecisionEmailEvent event = new ApplicationDecisionEmailEvent(
                10L, "jane.doe@example.com", "Subject", "Body");
        emailService.handleApplicationDecisionEmail(event);
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getFrom()).isEqualTo("noreply@ats.robottx.hu");
        assertThat(sent.getTo()).containsExactly("jane.doe@example.com");
        assertThat(sent.getSubject()).isEqualTo("Subject");
        assertThat(sent.getText()).isEqualTo("Body");
    }

    @Test
    @DisplayName("handleApplicationDecisionEmail catches mail sending failure gracefully")
    void handleApplicationDecisionEmailFailure() {
        when(mailConfig.getFrom()).thenReturn("noreply@ats.robottx.hu");
        doThrow(new RuntimeException("Mail server down")).when(mailSender).send(any(SimpleMailMessage.class));
        ApplicationDecisionEmailEvent event = new ApplicationDecisionEmailEvent(
                10L, "jane.doe@example.com", "Subject", "Body");
        emailService.handleApplicationDecisionEmail(event);
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    private Application createApplication(ApplicationStatus status) {
        Company company = Company.builder()
                .companyId(1L)
                .name("TechCorp")
                .build();
        EmailTemplate offerTemplate = EmailTemplate.builder()
                .emailTemplateId(1L)
                .company(company)
                .type(EmailTemplateType.OFFER)
                .subject("Offer for ${jobTitle} at ${companyName}")
                .body("Dear ${firstName} ${lastName}, we are pleased to offer you the "
                        + "${jobTitle} role in ${location} at ${companyName}.")
                .build();
        EmailTemplate declineTemplate = EmailTemplate.builder()
                .emailTemplateId(2L)
                .company(company)
                .type(EmailTemplateType.DECLINE)
                .subject("Update on your ${jobTitle} application at ${companyName}")
                .body("Dear ${firstName} ${lastName}, thank you for applying to "
                        + "${jobTitle} in ${location} at ${companyName}.")
                .build();
        JobListing jobListing = JobListing.builder()
                .jobListingId(1L)
                .company(company)
                .offerEmailTemplate(offerTemplate)
                .declineEmailTemplate(declineTemplate)
                .title("Senior Java Developer")
                .location("Budapest")
                .build();
        User user = User.builder()
                .userId("applicant-1")
                .firstName("Jane")
                .lastName("Doe")
                .email("jane.doe@example.com")
                .build();
        return Application.builder()
                .applicationId(10L)
                .jobListing(jobListing)
                .user(user)
                .status(status)
                .build();
    }

}
