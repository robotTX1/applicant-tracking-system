package hu.robottx.ats.provider;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.robottx.ats.UnitTest;

class DefaultEmailTemplateProviderTest extends UnitTest {

    @Test
    @DisplayName("getOfferTemplate and getDeclineTemplate lazily load and parse templates from classpath")
    void loadsTemplatesLazily() {
        DefaultEmailTemplateProvider provider = new DefaultEmailTemplateProvider(
                new ClassPathResource("templates/default-offer-template.txt"),
                new ClassPathResource("templates/default-decline-template.txt")
        );
        assertThat(provider.getOfferSubject()).isEqualTo("Job Offer: ${jobTitle} at ${companyName}");
        assertThat(provider.getOfferBody())
                .startsWith("Dear ${firstName} ${lastName},")
                .contains("${jobTitle}")
                .contains("${companyName}")
                .contains("${location}")
                .endsWith("${companyName} Team");
        assertThat(provider.getDeclineSubject())
                .isEqualTo("Update on your application for ${jobTitle} at ${companyName}");
        assertThat(provider.getDeclineBody())
                .startsWith("Dear ${firstName} ${lastName},")
                .contains("${jobTitle}")
                .contains("${companyName}")
                .contains("${location}")
                .endsWith("${companyName} Team");
        // Verify caching returns same instance
        assertThat(provider.getOfferTemplate()).isSameAs(provider.getOfferTemplate());
        assertThat(provider.getDeclineTemplate()).isSameAs(provider.getDeclineTemplate());
    }

    @Test
    @DisplayName("parseTemplate normalizes Windows CRLF line endings to LF")
    void normalizesWindowsLineEndings() {
        ByteArrayResource offerResource = new ByteArrayResource(
                "Subject Line\r\n\r\nLine 1\r\nLine 2".getBytes(StandardCharsets.UTF_8));
        ByteArrayResource declineResource = new ByteArrayResource(
                "Decline Subject\r\n\r\nDecline Body".getBytes(StandardCharsets.UTF_8));
        DefaultEmailTemplateProvider provider = new DefaultEmailTemplateProvider(offerResource, declineResource);
        assertThat(provider.getOfferSubject()).isEqualTo("Subject Line");
        assertThat(provider.getOfferBody()).isEqualTo("Line 1\nLine 2");
    }

    @Test
    @DisplayName("parseTemplate throws IllegalStateException when template has no newline separating subject")
    void throwsWhenNoNewline() {
        ByteArrayResource invalidResource = new ByteArrayResource(
                "Subject without any newline".getBytes(StandardCharsets.UTF_8));
        ByteArrayResource validResource = new ByteArrayResource(
                "Subject\n\nBody".getBytes(StandardCharsets.UTF_8));
        DefaultEmailTemplateProvider provider = new DefaultEmailTemplateProvider(invalidResource, validResource);
        assertThatThrownBy(provider::getOfferSubject)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Template resource must have a subject on the first line");
    }

}
