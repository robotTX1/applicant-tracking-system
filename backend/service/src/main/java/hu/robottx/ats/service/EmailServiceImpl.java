package hu.robottx.ats.service;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.text.StringSubstitutor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import hu.robottx.ats.config.MailConfig;
import hu.robottx.ats.domain.ApplicationStatus;
import hu.robottx.ats.entity.Application;
import hu.robottx.ats.entity.Company;
import hu.robottx.ats.entity.EmailTemplate;
import hu.robottx.ats.entity.JobListing;
import hu.robottx.ats.entity.User;
import hu.robottx.ats.event.ApplicationDecisionEmailEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final MailConfig mailConfig;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void sendApplicationDecisionEmail(Application application) {
        EmailTemplate template = resolveTemplate(application);
        if (template == null) {
            return;
        }
        User user = application.getUser();
        JobListing jobListing = application.getJobListing();
        Company company = jobListing.getCompany();
        Map<String, String> placeholders = Map.of(
                "firstName", user.getFirstName(),
                "lastName", user.getLastName(),
                "jobTitle", jobListing.getTitle(),
                "companyName", company.getName(),
                "location", jobListing.getLocation()
        );
        StringSubstitutor substitutor = new StringSubstitutor(placeholders);
        String subject = substitutor.replace(template.getSubject());
        String body = substitutor.replace(template.getBody());
        eventPublisher.publishEvent(new ApplicationDecisionEmailEvent(
                application.getApplicationId(),
                user.getEmail(),
                subject,
                body
        ));
    }

    @Async("emailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleApplicationDecisionEmail(ApplicationDecisionEmailEvent event) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailConfig.getFrom());
            message.setTo(event.recipientEmail());
            message.setSubject(event.subject());
            message.setText(event.body());
            log.debug("Sending decision email for application {} to {}",
                    event.applicationId(), event.recipientEmail());
            mailSender.send(message);
        } catch (Exception ex) {
            log.warn("Failed to send decision email for application {}: {}",
                    event.applicationId(), ex.getMessage(), ex);
        }
    }

    private EmailTemplate resolveTemplate(Application application) {
        if (application.getStatus() == ApplicationStatus.OFFER) {
            return application.getJobListing().getOfferEmailTemplate();
        }
        if (application.getStatus() == ApplicationStatus.DECLINED) {
            return application.getJobListing().getDeclineEmailTemplate();
        }
        return null;
    }

}
