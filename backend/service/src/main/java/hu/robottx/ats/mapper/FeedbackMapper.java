package hu.robottx.ats.mapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Page;

import hu.robottx.ats.domain.FeedbackType;
import hu.robottx.ats.entity.Feedback;
import hu.robottx.ats.model.ApplicationFeedbackType;
import hu.robottx.ats.model.FeedbackResponse;
import hu.robottx.ats.model.PagedFeedbackResponse;

@Mapper(uses = PageResponseMapper.class)
public interface FeedbackMapper {

    FeedbackMapper INSTANCE = Mappers.getMapper(FeedbackMapper.class);

    @Mapping(source = "content", target = "content")
    @Mapping(source = ".", target = "page")
    PagedFeedbackResponse toModel(Page<Feedback> feedbacks);

    @Mapping(source = "feedbackId", target = "id")
    @Mapping(source = "application.applicationId", target = "applicationId")
    @Mapping(source = "employee.employeeId", target = "employeeId")
    @Mapping(source = "type", target = "type")
    @Mapping(source = "feedback", target = "feedback")
    @Mapping(source = "score", target = "score")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    FeedbackResponse toModel(Feedback feedback);

    ApplicationFeedbackType toModel(FeedbackType type);

    FeedbackType toDomain(ApplicationFeedbackType type);

    default ZonedDateTime toZonedDateTime(Instant instant) {
        return instant != null ? instant.atZone(ZoneOffset.UTC) : null;
    }

}
