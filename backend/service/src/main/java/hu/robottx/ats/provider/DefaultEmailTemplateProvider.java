package hu.robottx.ats.provider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultEmailTemplateProvider {

    @Value("classpath:templates/default-offer-template.txt")
    private final Resource offerTemplateResource;
    @Value("classpath:templates/default-decline-template.txt")
    private final Resource declineTemplateResource;

    private DefaultEmailTemplate offerTemplate;
    private DefaultEmailTemplate declineTemplate;

    public synchronized DefaultEmailTemplate getOfferTemplate() {
        if (offerTemplate == null) {
            offerTemplate = parseTemplate(offerTemplateResource);
        }
        return offerTemplate;
    }

    public synchronized DefaultEmailTemplate getDeclineTemplate() {
        if (declineTemplate == null) {
            declineTemplate = parseTemplate(declineTemplateResource);
        }
        return declineTemplate;
    }

    public String getOfferSubject() {
        return getOfferTemplate().subject();
    }

    public String getOfferBody() {
        return getOfferTemplate().body();
    }

    public String getDeclineSubject() {
        return getDeclineTemplate().subject();
    }

    public String getDeclineBody() {
        return getDeclineTemplate().body();
    }

    private DefaultEmailTemplate parseTemplate(Resource resource) {
        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            String normalized = content.replace("\r\n", "\n");
            int firstNewline = normalized.indexOf('\n');
            if (firstNewline == -1) {
                throw new IllegalStateException(
                        "Template resource must have a subject on the first line: " + resource);
            }
            String subject = normalized.substring(0, firstNewline).trim();
            String body = normalized.substring(firstNewline + 1).trim();
            return new DefaultEmailTemplate(subject, body);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to load email template from " + resource, ex);
        }
    }

    public record DefaultEmailTemplate(String subject, String body) {
    }

}
