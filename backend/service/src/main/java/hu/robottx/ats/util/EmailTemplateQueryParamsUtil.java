package hu.robottx.ats.util;

import com.oracle.bmc.util.internal.StringUtils;

import java.util.HashMap;
import java.util.Map;

import lombok.experimental.UtilityClass;

import org.springframework.data.jpa.domain.Specification;

import hu.robottx.ats.entity.EmailTemplate;
import hu.robottx.ats.model.SearchAndPageRequest;
import io.github.perplexhub.rsql.RSQLJPASupport;

@UtilityClass
public class EmailTemplateQueryParamsUtil {

    private static final Map<String, String> PROPERTY_MAP = new HashMap<>();

    static {
        PROPERTY_MAP.put("id", "emailTemplateId");
        PROPERTY_MAP.put("companyId", "company.companyId");
        PROPERTY_MAP.put("type", "type");
        PROPERTY_MAP.put("subject", "subject");
        PROPERTY_MAP.put("body", "body");
        PROPERTY_MAP.put("createdAt", "createdAt");
        PROPERTY_MAP.put("updatedAt", "updatedAt");
    }

    public static Specification<EmailTemplate> createSearchSpecification(Long companyId, SearchAndPageRequest request) {
        Specification<EmailTemplate> spec =
                (root, query, cb) -> cb.equal(root.get("company").get("companyId"), companyId);
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
