package hu.robottx.ats.service;

import hu.robottx.ats.model.EmployeeResponse;
import hu.robottx.ats.model.EmployeeSaveRequest;
import hu.robottx.ats.model.EmployeeUpdateRequest;
import hu.robottx.ats.model.PagedEmployeeResponse;
import hu.robottx.ats.model.SearchAndPageRequest;

public interface EmployeeService {

    PagedEmployeeResponse getEmployees(Long companyId, SearchAndPageRequest request);

    EmployeeResponse getEmployeeById(Long companyId, Long employeeId);

    EmployeeResponse saveEmployee(Long companyId, EmployeeSaveRequest request);

    EmployeeResponse updateEmployee(Long companyId, Long employeeId, EmployeeUpdateRequest request);

    void deleteEmployee(Long companyId, Long employeeId);

}
