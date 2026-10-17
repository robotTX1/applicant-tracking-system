package hu.robottx.ats.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Page;

import hu.robottx.ats.domain.EmployeeRole;
import hu.robottx.ats.entity.Company;
import hu.robottx.ats.model.CompanyResponse;
import hu.robottx.ats.model.CompanyRole;
import hu.robottx.ats.model.CompanySaveRequest;
import hu.robottx.ats.model.CompanyUpdateRequest;
import hu.robottx.ats.model.PagedCompanyResponse;

@Mapper(uses = PageResponseMapper.class)
public interface CompanyMapper {

    CompanyMapper INSTANCE = Mappers.getMapper(CompanyMapper.class);

    @Mapping(source = "content", target = "content")
    @Mapping(source = ".", target = "page")
    PagedCompanyResponse toModel(Page<Company> companies);

    @Mapping(source = "companyId", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "foundingDate", target = "foundingDate")
    @Mapping(source = "websiteUrl", target = "websiteUrl")
    @Mapping(source = "description", target = "description")
    CompanyResponse toModel(Company company);

    @Mapping(target = "companyId", ignore = true)
    @Mapping(source = "name", target = "name")
    @Mapping(source = "foundingDate", target = "foundingDate")
    @Mapping(source = "websiteUrl", target = "websiteUrl")
    @Mapping(source = "description", target = "description")
    Company toEntity(CompanySaveRequest request);

    @Mapping(target = "companyId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(source = "name", target = "name")
    @Mapping(source = "foundingDate", target = "foundingDate")
    @Mapping(source = "websiteUrl", target = "websiteUrl")
    @Mapping(source = "description", target = "description")
    Company updateEntity(CompanyUpdateRequest request, @MappingTarget Company company);

    EmployeeRole toDomain(CompanyRole role);

}
