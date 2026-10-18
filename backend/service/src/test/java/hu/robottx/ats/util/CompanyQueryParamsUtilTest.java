package hu.robottx.ats.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import hu.robottx.ats.UnitTest;
import hu.robottx.ats.entity.Company;
import hu.robottx.ats.model.CompanyRole;
import hu.robottx.ats.model.SearchAndPageRequest;

class CompanyQueryParamsUtilTest extends UnitTest {

    @Test
    @DisplayName("employeeSpec throws when isEmployee is false but role is specified")
    void employeeSpecThrowsWhenFalseAndRoleSpecified() {
        assertThatThrownBy(() -> CompanyQueryParamsUtil.employeeSpec(Set.of(1L), false, CompanyRole.OWNER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot specify role when isEmployee is false");
    }

    @Test
    @DisplayName("employeeSpec returns non-null specification for null parameters")
    void employeeSpecReturnsNonNullForNullParameters() {
        Specification<Company> spec = CompanyQueryParamsUtil.employeeSpec(Collections.emptySet(), null, null);
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("employeeSpec returns non-null specification when isEmployee is true")
    void employeeSpecReturnsNonNullWhenIsEmployeeTrue() {
        Specification<Company> spec = CompanyQueryParamsUtil.employeeSpec(Set.of(1L, 2L), true, null);
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("employeeSpec returns non-null specification when isEmployee is true and company list is empty")
    void employeeSpecReturnsNonNullWhenIsEmployeeTrueAndEmpty() {
        Specification<Company> spec = CompanyQueryParamsUtil.employeeSpec(Collections.emptySet(), true, null);
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("employeeSpec returns non-null specification when isEmployee is false")
    void employeeSpecReturnsNonNullWhenIsEmployeeFalse() {
        Specification<Company> spec = CompanyQueryParamsUtil.employeeSpec(Set.of(1L), false, null);
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("employeeSpec returns non-null specification when isEmployee is false and company list is empty")
    void employeeSpecReturnsNonNullWhenIsEmployeeFalseAndEmpty() {
        Specification<Company> spec = CompanyQueryParamsUtil.employeeSpec(Collections.emptySet(), false, null);
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("createSearchSpecification handles null and empty request")
    void createSearchSpecificationNull() {
        assertThat(CompanyQueryParamsUtil.createSearchSpecification(null)).isNotNull();
        assertThat(CompanyQueryParamsUtil.createSearchSpecification(new SearchAndPageRequest())).isNotNull();
    }

}
