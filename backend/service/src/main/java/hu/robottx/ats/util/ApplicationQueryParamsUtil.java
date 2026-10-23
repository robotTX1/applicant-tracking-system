package hu.robottx.ats.util;

import com.oracle.bmc.util.internal.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import lombok.experimental.UtilityClass;

import org.springframework.data.jpa.domain.Specification;

import hu.robottx.ats.domain.ApplicationStatus;
import hu.robottx.ats.entity.Application;
import hu.robottx.ats.model.SearchAndPageRequest;
import io.github.perplexhub.rsql.RSQLJPASupport;

@UtilityClass
public class ApplicationQueryParamsUtil {

    private static final Map<String, String> USER_PROPERTY_MAP = new HashMap<>();
    private static final Map<String, String> JOB_LISTING_PROPERTY_MAP = new HashMap<>();

    static {
        USER_PROPERTY_MAP.put("id", "applicationId");
        USER_PROPERTY_MAP.put("jobListingId", "jobListing.jobListingId");
        USER_PROPERTY_MAP.put("companyId", "jobListing.company.companyId");
        USER_PROPERTY_MAP.put("userId", "user.userId");
        USER_PROPERTY_MAP.put("cvDocumentId", "cvDocument.documentId");
        USER_PROPERTY_MAP.put("applicantMessage", "applicantMessage");
        USER_PROPERTY_MAP.put("status", "status");
        USER_PROPERTY_MAP.put("createdAt", "createdAt");
        USER_PROPERTY_MAP.put("updatedAt", "updatedAt");
        USER_PROPERTY_MAP.put("aiReview", "unsupportedField");
        USER_PROPERTY_MAP.put("aiScore", "unsupportedField");

        JOB_LISTING_PROPERTY_MAP.putAll(USER_PROPERTY_MAP);
        JOB_LISTING_PROPERTY_MAP.put("aiReview", "aiReview");
        JOB_LISTING_PROPERTY_MAP.put("aiScore", "aiScore");
        JOB_LISTING_PROPERTY_MAP.put("firstName", "user.firstName");
        JOB_LISTING_PROPERTY_MAP.put("lastName", "user.lastName");
        JOB_LISTING_PROPERTY_MAP.put("email", "user.email");
    }

    public static Specification<Application> createUserSearchSpecification(
            String userId, SearchAndPageRequest request) {
        Specification<Application> spec =
                (root, query, cb) -> cb.equal(root.get("user").get("userId"), userId);
        return applyRequest(spec, request, USER_PROPERTY_MAP);
    }

    public static Specification<Application> createJobListingSearchSpecification(
            Long jobListingId, Set<ApplicationStatus> allowedStatuses, SearchAndPageRequest request) {
        Specification<Application> spec =
                (root, query, cb) -> cb.equal(root.get("jobListing").get("jobListingId"), jobListingId);
        if (allowedStatuses != null && !allowedStatuses.isEmpty()) {
            spec = spec.and((root, query, cb) -> root.get("status").in(allowedStatuses));
        }
        return applyRequest(spec, request, JOB_LISTING_PROPERTY_MAP);
    }

    private static Specification<Application> applyRequest(
            Specification<Application> baseSpec, SearchAndPageRequest request, Map<String, String> propertyMap) {
        if (request == null) {
            return baseSpec;
        }
        Specification<Application> spec = baseSpec;
        if (StringUtils.isNotBlank(request.getQuery())) {
            spec = spec.and(RSQLJPASupport.toSpecification(request.getQuery(), propertyMap));
        }
        if (StringUtils.isNotBlank(request.getSort())) {
            spec = spec.and(RSQLJPASupport.toSort(request.getSort(), propertyMap));
        }
        return spec;
    }

}
