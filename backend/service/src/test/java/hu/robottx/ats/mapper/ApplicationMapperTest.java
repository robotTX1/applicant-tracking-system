package hu.robottx.ats.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import hu.robottx.ats.UnitTest;
import hu.robottx.ats.domain.ApplicationStatus;
import hu.robottx.ats.entity.Application;
import hu.robottx.ats.entity.Document;
import hu.robottx.ats.entity.JobListing;
import hu.robottx.ats.entity.User;
import hu.robottx.ats.model.JobApplicationResponse;

class ApplicationMapperTest extends UnitTest {

    @Test
    @DisplayName("job application model includes the applicant's name and email")
    void jobApplicationModelIncludesApplicant() {
        Application application = Application.builder()
                .applicationId(5L)
                .jobListing(JobListing.builder().jobListingId(1L).build())
                .user(User.builder()
                        .userId("user-1")
                        .firstName("Jane")
                        .lastName("Smith")
                        .email("jane@example.com")
                        .build()
                )
                .cvDocument(Document.builder().documentId(9L).build())
                .aiScore(86)
                .aiReview("Strong match")
                .status(ApplicationStatus.AI_REVIEWED)
                .build();
        JobApplicationResponse response = ApplicationMapper.INSTANCE.toJobApplicationModel(application);
        assertThat(response.getUserId()).isEqualTo("user-1");
        assertThat(response.getApplicantFirstName()).isEqualTo("Jane");
        assertThat(response.getApplicantLastName()).isEqualTo("Smith");
        assertThat(response.getApplicantEmail()).isEqualTo("jane@example.com");
        assertThat(response.getAiScore()).isEqualTo(86);
    }

}
