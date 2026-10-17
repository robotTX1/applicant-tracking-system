package hu.robottx.ats.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Page;

import hu.robottx.ats.model.PageResponse;

@Mapper
public interface PageResponseMapper {

    PageResponseMapper INSTANCE = Mappers.getMapper(PageResponseMapper.class);

    @Mapping(source = "size", target = "size")
    @Mapping(source = "number", target = "number")
    @Mapping(source = "totalElements", target = "totalElements")
    @Mapping(source = "totalPages", target = "totalPages")
    PageResponse toModel(Page<?> page);

}
