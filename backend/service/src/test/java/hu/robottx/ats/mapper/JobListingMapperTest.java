package hu.robottx.ats.mapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import hu.robottx.ats.UnitTest;
import hu.robottx.ats.domain.JobStatus;
import hu.robottx.ats.entity.Company;
import hu.robottx.ats.entity.EmailTemplate;
import hu.robottx.ats.entity.JobListing;
import hu.robottx.ats.model.CompanyResponse;
import hu.robottx.ats.model.JobListingResponse;
import hu.robottx.ats.model.PagedJobListingResponse;

class JobListingMapperTest extends UnitTest {

    private static Company company(long id, String name) {
        return Company.builder()
                .companyId(id)
                .name(name)
                .foundingDate(LocalDate.of(2020, 1, 1))
                .websiteUrl("https://" + name.toLowerCase() + ".example.com")
                .description(name + " description")
                .build();
    }

    private static JobListing jobListing(long id, Company company) {
        return JobListing.builder()
                .jobListingId(id)
                .company(company)
                .offerEmailTemplate(EmailTemplate.builder().emailTemplateId(1L).build())
                .declineEmailTemplate(EmailTemplate.builder().emailTemplateId(2L).build())
                .title("Job " + id)
                .location("Budapest")
                .description("Description")
                .aiReviewEnabled(true)
                .aiReviewMinScore(70)
                .status(JobStatus.PUBLISHED)
                .build();
    }

    @Test
    @DisplayName("public model includes company details but no internal fields or application count")
    void publicModelIncludesCompany() {
        JobListingResponse response = JobListingMapper.INSTANCE.toPublicModel(jobListing(1L, company(10L, "Acme")));
        assertThat(response.getCompanyId()).isEqualTo(10L);
        assertThat(response.getCompany())
                .extracting(CompanyResponse::getId, CompanyResponse::getName, CompanyResponse::getWebsiteUrl,
                        CompanyResponse::getDescription)
                .containsExactly(10L, "Acme", "https://acme.example.com", "Acme description");
        assertThat(response.getAiReviewEnabled()).isNull();
        assertThat(response.getOfferEmailTemplateId()).isNull();
        assertThat(response.getApplicationCount()).isNull();
    }

    @Test
    @DisplayName("employee model includes company details, internal fields and the application count")
    void employeeModelIncludesCount() {
        JobListingResponse response = JobListingMapper.INSTANCE.toModel(jobListing(1L, company(10L, "Acme")), 7L);
        assertThat(response.getCompany().getName()).isEqualTo("Acme");
        assertThat(response.getAiReviewEnabled()).isTrue();
        assertThat(response.getOfferEmailTemplateId()).isEqualTo(1L);
        assertThat(response.getApplicationCount()).isEqualTo(7L);
    }

    @Test
    @DisplayName("page mapping uses counts only for the user's companies and defaults missing counts to 0")
    void pageMappingAppliesCountsToOwnListings() {
        Company own = company(10L, "Acme");
        Company other = company(20L, "Globex");
        var page = new PageImpl<>(
                List.of(jobListing(1L, own), jobListing(2L, own), jobListing(3L, other)),
                PageRequest.of(0, 20), 3);
        PagedJobListingResponse response = JobListingMapper.INSTANCE.toModel(page, Set.of(10L), Map.of(1L, 4L));
        assertThat(response.getContent())
                .extracting(JobListingResponse::getId, JobListingResponse::getApplicationCount)
                .containsExactly(
                        tuple(1L, 4L),
                        tuple(2L, 0L),
                        tuple(3L, null));
        assertThat(response.getContent().get(2).getCompany().getName()).isEqualTo("Globex");
        assertThat(response.getContent().get(2).getAiReviewEnabled()).isNull();
    }

}
