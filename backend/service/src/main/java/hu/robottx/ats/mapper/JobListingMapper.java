package hu.robottx.ats.mapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Page;

import hu.robottx.ats.domain.JobStatus;
import hu.robottx.ats.entity.JobListing;
import hu.robottx.ats.model.JobListingResponse;
import hu.robottx.ats.model.JobListingStatus;
import hu.robottx.ats.model.PagedJobListingResponse;

@Mapper(uses = PageResponseMapper.class)
public interface JobListingMapper {

    JobListingMapper INSTANCE = Mappers.getMapper(JobListingMapper.class);

    @Mapping(source = "content", target = "content")
    @Mapping(source = ".", target = "page")
    PagedJobListingResponse toModel(Page<JobListing> jobListings);

    @Mapping(source = "jobListingId", target = "id")
    @Mapping(source = "company.companyId", target = "companyId")
    @Mapping(source = "location", target = "location")
    @Mapping(source = "title", target = "title")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "aiReviewEnabled", target = "aiReviewEnabled")
    @Mapping(source = "aiReviewMinScore", target = "aiReviewMinScore")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    JobListingResponse toModel(JobListing jobListing);

    JobListingStatus toModel(JobStatus status);

    JobStatus toDomain(JobListingStatus status);

    default ZonedDateTime toZonedDateTime(Instant instant) {
        return instant != null ? instant.atZone(ZoneOffset.UTC) : null;
    }

}
