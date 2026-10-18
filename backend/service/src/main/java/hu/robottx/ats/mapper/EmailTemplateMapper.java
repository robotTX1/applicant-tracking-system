package hu.robottx.ats.mapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Page;

import hu.robottx.ats.domain.EmailTemplateType;
import hu.robottx.ats.entity.EmailTemplate;
import hu.robottx.ats.model.CompanyEmailTemplateType;
import hu.robottx.ats.model.EmailTemplateResponse;
import hu.robottx.ats.model.PagedEmailTemplateResponse;

@Mapper(uses = PageResponseMapper.class)
public interface EmailTemplateMapper {

    EmailTemplateMapper INSTANCE = Mappers.getMapper(EmailTemplateMapper.class);

    @Mapping(source = "content", target = "content")
    @Mapping(source = ".", target = "page")
    PagedEmailTemplateResponse toModel(Page<EmailTemplate> emailTemplates);

    @Mapping(source = "emailTemplateId", target = "id")
    @Mapping(source = "company.companyId", target = "companyId")
    @Mapping(source = "type", target = "type")
    @Mapping(source = "subject", target = "subject")
    @Mapping(source = "body", target = "body")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "updatedAt", target = "updatedAt")
    EmailTemplateResponse toModel(EmailTemplate emailTemplate);

    CompanyEmailTemplateType toModel(EmailTemplateType type);

    EmailTemplateType toDomain(CompanyEmailTemplateType type);

    default ZonedDateTime toZonedDateTime(Instant instant) {
        return instant != null ? instant.atZone(ZoneOffset.UTC) : null;
    }

}
