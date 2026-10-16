package hu.robottx.ats.controller;

import static hu.robottx.ats.controller.RestControllerConstants.EMPLOYEE_BY_ID_ENDPOINT;

import java.net.URI;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import hu.robottx.ats.api.EmployeesApiDelegate;
import hu.robottx.ats.model.EmployeeResponse;
import hu.robottx.ats.model.EmployeeSaveRequest;
import hu.robottx.ats.model.EmployeeUpdateRequest;
import hu.robottx.ats.model.PagedEmployeeResponse;
import hu.robottx.ats.model.SearchAndPageRequest;
import hu.robottx.ats.service.EmployeeService;

@Component
@RequiredArgsConstructor
public class EmployeesApiDelegateImpl implements EmployeesApiDelegate {

    private final EmployeeService employeeService;

    @Override
    public ResponseEntity<PagedEmployeeResponse> getEmployees(Long companyId,
            SearchAndPageRequest searchAndPageRequest) {
        PagedEmployeeResponse response = employeeService.getEmployees(companyId, searchAndPageRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<EmployeeResponse> getEmployee(Long companyId, Long employeeId) {
        EmployeeResponse response = employeeService.getEmployeeById(companyId, employeeId);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<EmployeeResponse> saveEmployee(Long companyId, EmployeeSaveRequest employeeSaveRequest) {
        EmployeeResponse response = employeeService.saveEmployee(companyId, employeeSaveRequest);
        return ResponseEntity.created(buildUri(companyId, response.getId())).body(response);
    }

    @Override
    public ResponseEntity<EmployeeResponse> updateEmployee(Long companyId, Long employeeId,
            EmployeeUpdateRequest employeeUpdateRequest) {
        EmployeeResponse response = employeeService.updateEmployee(companyId, employeeId, employeeUpdateRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteEmployee(Long companyId, Long employeeId) {
        employeeService.deleteEmployee(companyId, employeeId);
        return ResponseEntity.noContent().build();
    }

    private URI buildUri(Long companyId, Long employeeId) {
        return UriComponentsBuilder.fromUriString(EMPLOYEE_BY_ID_ENDPOINT)
                .buildAndExpand(companyId, employeeId)
                .toUri();
    }

}
