package hu.robottx.ats.util;

import com.oracle.bmc.util.internal.StringUtils;

import java.util.HashMap;
import java.util.Map;

import lombok.experimental.UtilityClass;

import org.springframework.data.jpa.domain.Specification;

import hu.robottx.ats.domain.EmployeeStatus;
import hu.robottx.ats.entity.Employee;
import hu.robottx.ats.model.SearchAndPageRequest;
import io.github.perplexhub.rsql.RSQLJPASupport;

@UtilityClass
public class EmployeeQueryParamsUtil {

    private static final Map<String, String> PROPERTY_MAP = new HashMap<>();

    static {
        PROPERTY_MAP.put("id", "employeeId");
        PROPERTY_MAP.put("userId", "user.userId");
        PROPERTY_MAP.put("companyId", "company.companyId");
        PROPERTY_MAP.put("role", "role");
        PROPERTY_MAP.put("status", "status");
        PROPERTY_MAP.put("assignedDate", "assignedDate");
        PROPERTY_MAP.put("firstName", "user.firstName");
        PROPERTY_MAP.put("lastName", "user.lastName");
        PROPERTY_MAP.put("email", "user.email");
    }

    public static Specification<Employee> createSearchSpecification(Long companyId, SearchAndPageRequest request) {
        Specification<Employee> spec = (root, query, cb) -> cb.equal(root.get("company").get("companyId"), companyId);
        if (request == null) {
            return spec.and((root, query, cb) -> cb.equal(root.get("status"), EmployeeStatus.ACTIVE));
        }
        if (StringUtils.isBlank(request.getQuery()) || !request.getQuery().contains("status")) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), EmployeeStatus.ACTIVE));
        }
        if (StringUtils.isNotBlank(request.getQuery())) {
            spec = spec.and(RSQLJPASupport.toSpecification(request.getQuery(), PROPERTY_MAP));
        }
        if (StringUtils.isNotBlank(request.getSort())) {
            spec = spec.and(RSQLJPASupport.toSort(request.getSort(), PROPERTY_MAP));
        }
        return spec;
    }

}
