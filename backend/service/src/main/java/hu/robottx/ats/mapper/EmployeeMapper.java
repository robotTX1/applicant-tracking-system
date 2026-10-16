package hu.robottx.ats.mapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Page;

import hu.robottx.ats.domain.EmployeeRole;
import hu.robottx.ats.domain.EmployeeStatus;
import hu.robottx.ats.entity.Employee;
import hu.robottx.ats.model.CompanyEmployeeStatus;
import hu.robottx.ats.model.CompanyRole;
import hu.robottx.ats.model.EmployeeResponse;
import hu.robottx.ats.model.PagedEmployeeResponse;

@Mapper(uses = PageResponseMapper.class)
public interface EmployeeMapper {

    EmployeeMapper INSTANCE = Mappers.getMapper(EmployeeMapper.class);

    @Mapping(source = "content", target = "content")
    @Mapping(source = ".", target = "page")
    PagedEmployeeResponse toModel(Page<Employee> employees);

    @Mapping(source = "employeeId", target = "id")
    @Mapping(source = "company.companyId", target = "companyId")
    @Mapping(source = "user.userId", target = "userId")
    @Mapping(source = "role", target = "role")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "assignedDate", target = "assignedDate")
    EmployeeResponse toModel(Employee employee);

    CompanyRole toModel(EmployeeRole role);

    CompanyEmployeeStatus toModel(EmployeeStatus status);

    EmployeeRole toDomain(CompanyRole role);

    EmployeeStatus toDomain(CompanyEmployeeStatus status);

    default ZonedDateTime toZonedDateTime(Instant instant) {
        return instant != null ? instant.atZone(ZoneOffset.UTC) : null;
    }

}
