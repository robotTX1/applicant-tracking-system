package hu.robottx.ats.util;

import com.oracle.bmc.util.internal.StringUtils;

import java.util.HashMap;
import java.util.Map;

import lombok.experimental.UtilityClass;

import org.springframework.data.jpa.domain.Specification;

import hu.robottx.ats.domain.FeedbackType;
import hu.robottx.ats.entity.Feedback;
import hu.robottx.ats.model.SearchAndPageRequest;
import io.github.perplexhub.rsql.RSQLJPASupport;

@UtilityClass
public class FeedbackQueryParamsUtil {

    private static final Map<String, String> PROPERTY_MAP = new HashMap<>();

    static {
        PROPERTY_MAP.put("id", "feedbackId");
        PROPERTY_MAP.put("applicationId", "application.applicationId");
        PROPERTY_MAP.put("employeeId", "employee.employeeId");
        PROPERTY_MAP.put("type", "type");
        PROPERTY_MAP.put("feedback", "feedback");
        PROPERTY_MAP.put("score", "score");
        PROPERTY_MAP.put("createdAt", "createdAt");
        PROPERTY_MAP.put("updatedAt", "updatedAt");
    }

    public static Specification<Feedback> createSearchSpecification(
            Long applicationId, FeedbackType restrictedType, SearchAndPageRequest request) {
        Specification<Feedback> spec =
                (root, query, cb) -> cb.equal(root.get("application").get("applicationId"), applicationId);
        if (restrictedType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), restrictedType));
        }
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

}
