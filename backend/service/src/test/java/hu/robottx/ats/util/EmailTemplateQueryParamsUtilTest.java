package hu.robottx.ats.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import hu.robottx.ats.UnitTest;
import hu.robottx.ats.entity.EmailTemplate;
import hu.robottx.ats.model.SearchAndPageRequest;

class EmailTemplateQueryParamsUtilTest extends UnitTest {

    @Test
    @DisplayName("createSearchSpecification handles null request")
    void createSearchSpecificationNullRequest() {
        Specification<EmailTemplate> spec = EmailTemplateQueryParamsUtil.createSearchSpecification(1L, null);
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("createSearchSpecification handles empty request")
    void createSearchSpecificationEmptyRequest() {
        Specification<EmailTemplate> spec =
                EmailTemplateQueryParamsUtil.createSearchSpecification(1L, new SearchAndPageRequest());
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("createSearchSpecification handles query and sort parameters")
    void createSearchSpecificationWithQueryAndSort() {
        SearchAndPageRequest request = new SearchAndPageRequest();
        request.setQuery("type==OFFER");
        request.setSort("id,asc");
        Specification<EmailTemplate> spec = EmailTemplateQueryParamsUtil.createSearchSpecification(1L, request);
        assertThat(spec).isNotNull();
    }

}
