package hu.robottx.ats.mapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Page;

import hu.robottx.ats.domain.JobStatus;
import hu.robottx.ats.entity.JobListing;
import hu.robottx.ats.model.JobListingResponse;
import hu.robottx.ats.model.JobListingStatus;
import hu.robottx.ats.model.PagedJobListingResponse;

@Mapper(uses = {PageResponseMapper.class, CompanyMapper.class})
public interface JobListingMapper {

    JobListingMapper INSTANCE = Mappers.getMapper(JobListingMapper.class);

    default PagedJobListingResponse toModel(Page<JobListing> jobListings, Set<Long> employeeCompanyIds,
            Map<Long, Long> applicationCounts) {
        if (jobListings == null) {
            return null;
        }
        List<JobListingResponse> content = jobListings.getContent().stream()
                .map(jl -> toCorrectModel(jl, employeeCompanyIds, applicationCounts))
                .toList();
        return PagedJobListingResponse.builder()
                .content(content)
                .page(PageResponseMapper.INSTANCE.toModel(jobListings))
                .build();
    }

    @Mapping(source = "jobListingId", target = "id")
    @Mapping(source = "company.companyId", target = "companyId")
    @Mapping(source = "location", target = "location")
    @Mapping(source = "title", target = "title")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "aiReviewEnabled", target = "aiReviewEnabled")
    @Mapping(source = "aiReviewMinScore", target = "aiReviewMinScore")
    @Mapping(source = "offerEmailTemplate.emailTemplateId", target = "offerEmailTemplateId")
    @Mapping(source = "declineEmailTemplate.emailTemplateId", target = "declineEmailTemplateId")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    @Mapping(source = "company", target = "company")
    @Mapping(target = "applicationCount", ignore = true)
    JobListingResponse toModel(JobListing jobListing);

    default JobListingResponse toModel(JobListing jobListing, long applicationCount) {
        JobListingResponse response = toModel(jobListing);
        if (response != null) {
            response.setApplicationCount(applicationCount);
        }
        return response;
    }

    JobListingStatus toModel(JobStatus status);

    @Named("toPublicModel")
    @Mapping(source = "jobListingId", target = "id")
    @Mapping(source = "company.companyId", target = "companyId")
    @Mapping(source = "location", target = "location")
    @Mapping(source = "title", target = "title")
    @Mapping(source = "description", target = "description")
    @Mapping(target = "aiReviewEnabled", ignore = true)
    @Mapping(target = "aiReviewMinScore", ignore = true)
    @Mapping(target = "offerEmailTemplateId", ignore = true)
    @Mapping(target = "declineEmailTemplateId", ignore = true)
    @Mapping(target = "applicationCount", ignore = true)
    @Mapping(source = "status", target = "status")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    @Mapping(source = "company", target = "company")
    JobListingResponse toPublicModel(JobListing jobListing);

    JobStatus toDomain(JobListingStatus status);

    default ZonedDateTime toZonedDateTime(Instant instant) {
        return instant != null ? instant.atZone(ZoneOffset.UTC) : null;
    }

    private JobListingResponse toCorrectModel(JobListing jobListing, Set<Long> employeeCompanyIds,
            Map<Long, Long> applicationCounts) {
        JobListingResponse response;
        if (CollectionUtils.containsAny(employeeCompanyIds, jobListing.getCompany().getCompanyId())) {
            response = toModel(jobListing, applicationCounts.getOrDefault(jobListing.getJobListingId(), 0L));
        } else {
            response = toPublicModel(jobListing);
        }
        return response;
    }

}
