package hu.robottx.ats.util;

import com.oracle.bmc.util.internal.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import lombok.experimental.UtilityClass;

import org.springframework.data.jpa.domain.Specification;

import hu.robottx.ats.entity.Company;
import hu.robottx.ats.model.CompanyRole;
import hu.robottx.ats.model.SearchAndPageRequest;
import io.github.perplexhub.rsql.RSQLJPASupport;

@UtilityClass
public class CompanyQueryParamsUtil {

    private static final Map<String, String> PROPERTY_MAP = new HashMap<>();

    static {
        PROPERTY_MAP.put("id", "companyId");
        PROPERTY_MAP.put("name", "name");
        PROPERTY_MAP.put("foundingDate", "foundingDate");
        PROPERTY_MAP.put("websiteUrl", "websiteUrl");
        PROPERTY_MAP.put("description", "description");
    }

    public static Specification<Company> createSearchSpecification(SearchAndPageRequest request) {
        Specification<Company> spec = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        if (request == null) {
            return spec;
        }
        if (StringUtils.isNotBlank(request.getQuery())) {
            spec = spec.and(RSQLJPASupport.toSpecification(request.getQuery(), PROPERTY_MAP));
        }
        if (StringUtils.isNotBlank(request.getSort())) {
            spec = spec.and(RSQLJPASupport.toSort(request.getSort(), PROPERTY_MAP));
        }
        return spec;
    }

    public static Specification<Company> employeeSpec(Set<Long> userCompanyIds, Boolean isEmployee, CompanyRole role) {
        if (Boolean.FALSE.equals(isEmployee) && role != null) {
            throw new IllegalArgumentException("Cannot specify role when isEmployee is false");
        }
        if (isEmployee == null && role == null) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        }
        if (Boolean.FALSE.equals(isEmployee)) {
            if (userCompanyIds == null || userCompanyIds.isEmpty()) {
                return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
            }
            return (root, query, criteriaBuilder) -> criteriaBuilder.not(root.get("companyId").in(userCompanyIds));
        }
        if (userCompanyIds == null || userCompanyIds.isEmpty()) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.disjunction();
        }
        return (root, query, criteriaBuilder) -> root.get("companyId").in(userCompanyIds);
    }

}
