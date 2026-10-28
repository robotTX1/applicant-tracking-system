package hu.robottx.ats.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import hu.robottx.ats.UnitTest;
import hu.robottx.ats.domain.FeedbackType;
import hu.robottx.ats.entity.Feedback;
import hu.robottx.ats.model.SearchAndPageRequest;

class FeedbackQueryParamsUtilTest extends UnitTest {

    @Test
    @DisplayName("createSearchSpecification handles null and empty request")
    void createSearchSpecificationNullAndEmpty() {
        assertThat(FeedbackQueryParamsUtil.createSearchSpecification(1L, null, null)).isNotNull();
        assertThat(FeedbackQueryParamsUtil.createSearchSpecification(1L, FeedbackType.TECHNICAL,
                new SearchAndPageRequest())).isNotNull();
    }

    @Test
    @DisplayName("createSearchSpecification handles query and sort parameters")
    void createSearchSpecificationWithQueryAndSort() {
        SearchAndPageRequest request = new SearchAndPageRequest();
        request.setQuery("score>=80");
        request.setSort("score,desc");
        Specification<Feedback> spec =
                FeedbackQueryParamsUtil.createSearchSpecification(1L, FeedbackType.TECHNICAL, request);
        assertThat(spec).isNotNull();
    }

}
