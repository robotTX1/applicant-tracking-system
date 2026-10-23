package hu.robottx.ats.mapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Page;

import hu.robottx.ats.domain.ApplicationStatus;
import hu.robottx.ats.entity.Application;
import hu.robottx.ats.model.JobApplicationResponse;
import hu.robottx.ats.model.JobApplicationStatus;
import hu.robottx.ats.model.PagedJobApplicationResponse;
import hu.robottx.ats.model.PagedUserApplicationResponse;
import hu.robottx.ats.model.UserApplicationResponse;

@Mapper(uses = PageResponseMapper.class)
public interface ApplicationMapper {

    ApplicationMapper INSTANCE = Mappers.getMapper(ApplicationMapper.class);

    @Mapping(source = "content", target = "content")
    @Mapping(source = ".", target = "page")
    PagedUserApplicationResponse toUserApplicationPageModel(Page<Application> applications);

    @Mapping(source = "applicationId", target = "id")
    @Mapping(source = "jobListing.jobListingId", target = "jobListingId")
    @Mapping(source = "user.userId", target = "userId")
    @Mapping(source = "cvDocument.documentId", target = "cvDocumentId")
    @Mapping(source = "applicantMessage", target = "applicantMessage")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    UserApplicationResponse toUserApplicationModel(Application application);

    @Mapping(source = "content", target = "content")
    @Mapping(source = ".", target = "page")
    PagedJobApplicationResponse toJobApplicationPageModel(Page<Application> applications);

    @Mapping(source = "applicationId", target = "id")
    @Mapping(source = "jobListing.jobListingId", target = "jobListingId")
    @Mapping(source = "user.userId", target = "userId")
    @Mapping(source = "cvDocument.documentId", target = "cvDocumentId")
    @Mapping(source = "applicantMessage", target = "applicantMessage")
    @Mapping(source = "aiReview", target = "aiReview")
    @Mapping(source = "aiScore", target = "aiScore")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    JobApplicationResponse toJobApplicationModel(Application application);

    JobApplicationStatus toModel(ApplicationStatus status);

    ApplicationStatus toDomain(JobApplicationStatus status);

    default ZonedDateTime toZonedDateTime(Instant instant) {
        return instant != null ? instant.atZone(ZoneOffset.UTC) : null;
    }

}
