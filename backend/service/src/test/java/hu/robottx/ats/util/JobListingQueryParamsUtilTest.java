package hu.robottx.ats.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import hu.robottx.ats.UnitTest;
import hu.robottx.ats.entity.JobListing;
import hu.robottx.ats.model.SearchAndPageRequest;

class JobListingQueryParamsUtilTest extends UnitTest {

    @Test
    @DisplayName("visibilitySpec returns specification when authorized company IDs are empty")
    void visibilitySpecEmpty() {
        Specification<JobListing> spec = JobListingQueryParamsUtil.visibilitySpec(Collections.emptySet());
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("visibilitySpec returns specification when authorized company IDs are null")
    void visibilitySpecNull() {
        Specification<JobListing> spec = JobListingQueryParamsUtil.visibilitySpec(null);
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("visibilitySpec returns specification when authorized company IDs are present")
    void visibilitySpecPresent() {
        Specification<JobListing> spec = JobListingQueryParamsUtil.visibilitySpec(Set.of(1L, 2L));
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("createSearchSpecification handles null and empty request")
    void createSearchSpecification() {
        assertThat(JobListingQueryParamsUtil.createSearchSpecification(Set.of(1L), null)).isNotNull();
        assertThat(JobListingQueryParamsUtil.createSearchSpecification(Set.of(1L), new SearchAndPageRequest()))
                .isNotNull();
    }

}
