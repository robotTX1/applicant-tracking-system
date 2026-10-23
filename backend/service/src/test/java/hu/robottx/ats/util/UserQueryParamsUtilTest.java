package hu.robottx.ats.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import hu.robottx.ats.UnitTest;
import hu.robottx.ats.entity.User;
import hu.robottx.ats.model.SearchAndPageRequest;

class UserQueryParamsUtilTest extends UnitTest {

    @Test
    @DisplayName("createSearchSpecification handles null and empty request")
    void createSearchSpecificationNullAndEmpty() {
        assertThat(UserQueryParamsUtil.createSearchSpecification(null)).isNotNull();
        assertThat(UserQueryParamsUtil.createSearchSpecification(new SearchAndPageRequest())).isNotNull();
    }

    @Test
    @DisplayName("createSearchSpecification handles query and sort parameters")
    void createSearchSpecificationWithQueryAndSort() {
        SearchAndPageRequest request = new SearchAndPageRequest();
        request.setQuery("firstName=='Test'");
        request.setSort("lastName,asc");
        Specification<User> spec = UserQueryParamsUtil.createSearchSpecification(request);
        assertThat(spec).isNotNull();
    }

}
