package hu.robottx.ats.util;

import com.oracle.bmc.util.internal.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import lombok.experimental.UtilityClass;

import org.springframework.data.jpa.domain.Specification;

import hu.robottx.ats.domain.JobStatus;
import hu.robottx.ats.entity.JobListing;
import hu.robottx.ats.model.SearchAndPageRequest;
import io.github.perplexhub.rsql.RSQLJPASupport;

@UtilityClass
public class JobListingQueryParamsUtil {

    private static final Map<String, String> PROPERTY_MAP = new HashMap<>();

    static {
        PROPERTY_MAP.put("id", "jobListingId");
        PROPERTY_MAP.put("companyId", "company.companyId");
        PROPERTY_MAP.put("location", "location");
        PROPERTY_MAP.put("title", "title");
        PROPERTY_MAP.put("description", "description");
        PROPERTY_MAP.put("aiReviewEnabled", "aiReviewEnabled");
        PROPERTY_MAP.put("aiReviewMinScore", "aiReviewMinScore");
        PROPERTY_MAP.put("status", "status");
        PROPERTY_MAP.put("createdAt", "createdAt");
        PROPERTY_MAP.put("updatedAt", "updatedAt");
    }

    public static Specification<JobListing> createSearchSpecification(
            Set<Long> authorizedCompanyIds, SearchAndPageRequest request) {
        Specification<JobListing> spec = visibilitySpec(authorizedCompanyIds);
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

    public static Specification<JobListing> visibilitySpec(Set<Long> authorizedCompanyIds) {
        if (authorizedCompanyIds == null || authorizedCompanyIds.isEmpty()) {
            return (root, query, cb) -> cb.equal(root.get("status"), JobStatus.PUBLISHED);
        }
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("status"), JobStatus.PUBLISHED),
                root.get("company").get("companyId").in(authorizedCompanyIds)
        );
    }

}
