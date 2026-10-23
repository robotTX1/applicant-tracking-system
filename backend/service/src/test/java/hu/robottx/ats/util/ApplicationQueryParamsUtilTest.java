package hu.robottx.ats.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import hu.robottx.ats.UnitTest;
import hu.robottx.ats.domain.ApplicationStatus;
import hu.robottx.ats.entity.Application;
import hu.robottx.ats.model.SearchAndPageRequest;

class ApplicationQueryParamsUtilTest extends UnitTest {

    @Test
    @DisplayName("createUserSearchSpecification handles null and empty request")
    void createUserSearchSpecificationNullAndEmpty() {
        assertThat(ApplicationQueryParamsUtil.createUserSearchSpecification("test-user", null)).isNotNull();
        assertThat(ApplicationQueryParamsUtil.createUserSearchSpecification("test-user", new SearchAndPageRequest()))
                .isNotNull();
    }

    @Test
    @DisplayName("createUserSearchSpecification handles query and sort parameters")
    void createUserSearchSpecificationWithQueryAndSort() {
        SearchAndPageRequest request = new SearchAndPageRequest();
        request.setQuery("status==APPLIED");
        request.setSort("id,asc");
        Specification<Application> spec =
                ApplicationQueryParamsUtil.createUserSearchSpecification("test-user", request);
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("createJobListingSearchSpecification handles null and filtered statuses")
    void createJobListingSearchSpecification() {
        Set<ApplicationStatus> technicalStatuses = EnumSet.of(
                ApplicationStatus.TECHNICAL_INTERVIEW_SCHEDULED,
                ApplicationStatus.TECHNICAL_INTERVIEWED);
        SearchAndPageRequest request = new SearchAndPageRequest();
        request.setQuery("aiScore>=70");
        request.setSort("aiScore,desc");
        assertThat(ApplicationQueryParamsUtil.createJobListingSearchSpecification(1L, null, null)).isNotNull();
        assertThat(ApplicationQueryParamsUtil.createJobListingSearchSpecification(1L, technicalStatuses, request))
                .isNotNull();
    }

}
