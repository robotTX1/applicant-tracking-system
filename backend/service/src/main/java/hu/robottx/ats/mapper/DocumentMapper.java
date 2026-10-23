package hu.robottx.ats.mapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import hu.robottx.ats.entity.Document;
import hu.robottx.ats.model.DocumentResponse;

@Mapper
public interface DocumentMapper {

    DocumentMapper INSTANCE = Mappers.getMapper(DocumentMapper.class);

    @Mapping(source = "documentId", target = "id")
    @Mapping(source = "originalDocumentName", target = "originalDocumentName")
    @Mapping(source = "mimeType", target = "mimeType")
    @Mapping(source = "uploadedAt", target = "uploadedAt")
    DocumentResponse toModel(Document document);

    default ZonedDateTime toZonedDateTime(Instant instant) {
        return instant != null ? instant.atZone(ZoneOffset.UTC) : null;
    }

}
