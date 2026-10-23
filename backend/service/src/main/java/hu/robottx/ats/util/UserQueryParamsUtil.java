package hu.robottx.ats.util;

import com.oracle.bmc.util.internal.StringUtils;

import java.util.HashMap;
import java.util.Map;

import lombok.experimental.UtilityClass;

import org.springframework.data.jpa.domain.Specification;

import hu.robottx.ats.entity.User;
import hu.robottx.ats.model.SearchAndPageRequest;
import io.github.perplexhub.rsql.RSQLJPASupport;

@UtilityClass
public class UserQueryParamsUtil {

    private static final Map<String, String> PROPERTY_MAP = new HashMap<>();
    private static final Map<String, String> SORT_PROPERTY_MAP = new HashMap<>();

    static {
        PROPERTY_MAP.put("firstName", "firstName");
        PROPERTY_MAP.put("lastName", "lastName");
        PROPERTY_MAP.put("email", "email");
        PROPERTY_MAP.put("id", "unsupportedField");
        PROPERTY_MAP.put("userId", "unsupportedField");
        PROPERTY_MAP.put("phoneNumber", "unsupportedField");
        PROPERTY_MAP.put("phone", "unsupportedField");
        PROPERTY_MAP.put("cvDocument", "unsupportedField");
        PROPERTY_MAP.put("cvDocumentId", "unsupportedField");
        PROPERTY_MAP.put("createdAt", "unsupportedField");
        PROPERTY_MAP.put("updatedAt", "unsupportedField");

        SORT_PROPERTY_MAP.put("id", "userId");
        SORT_PROPERTY_MAP.put("firstName", "firstName");
        SORT_PROPERTY_MAP.put("lastName", "lastName");
        SORT_PROPERTY_MAP.put("email", "email");
        SORT_PROPERTY_MAP.put("phoneNumber", "unsupportedField");
        SORT_PROPERTY_MAP.put("phone", "unsupportedField");
        SORT_PROPERTY_MAP.put("cvDocument", "unsupportedField");
        SORT_PROPERTY_MAP.put("cvDocumentId", "unsupportedField");
        SORT_PROPERTY_MAP.put("createdAt", "unsupportedField");
        SORT_PROPERTY_MAP.put("updatedAt", "unsupportedField");
    }

    public static Specification<User> createSearchSpecification(SearchAndPageRequest request) {
        Specification<User> spec = (root, query, cb) -> cb.conjunction();
        if (request == null) {
            return spec;
        }
        if (StringUtils.isNotBlank(request.getQuery())) {
            spec = spec.and(RSQLJPASupport.toSpecification(request.getQuery(), PROPERTY_MAP));
        }
        if (StringUtils.isNotBlank(request.getSort())) {
            spec = spec.and(RSQLJPASupport.toSort(request.getSort(), SORT_PROPERTY_MAP));
        }
        return spec;
    }

}
