package hu.robottx.ats.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hu.robottx.ats.domain.EmployeeRole;
import hu.robottx.ats.domain.EmployeeStatus;
import hu.robottx.ats.entity.Company;
import hu.robottx.ats.entity.Employee;
import hu.robottx.ats.entity.User;
import hu.robottx.ats.exception.ConflictException;
import hu.robottx.ats.exception.ResourceNotFoundException;
import hu.robottx.ats.mapper.EmployeeMapper;
import hu.robottx.ats.model.CompanyRole;
import hu.robottx.ats.model.EmployeeResponse;
import hu.robottx.ats.model.EmployeeSaveRequest;
import hu.robottx.ats.model.EmployeeUpdateRequest;
import hu.robottx.ats.model.PagedEmployeeResponse;
import hu.robottx.ats.model.SearchAndPageRequest;
import hu.robottx.ats.repository.CompanyRepository;
import hu.robottx.ats.repository.EmployeeRepository;
import hu.robottx.ats.repository.UserRepository;
import hu.robottx.ats.security.service.SecurityService;
import hu.robottx.ats.util.EmployeeQueryParamsUtil;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private static final String COMPANY_NOT_FOUND_MESSAGE = "No company with id: %d";
    private static final String EMPLOYEE_NOT_FOUND_MESSAGE = "No employee with id: %d";
    private static final String USER_NOT_FOUND_MESSAGE = "No user with id: %s";
    private static final Set<EmployeeRole> OWNER_AND_CO_OWNER = Set.of(EmployeeRole.OWNER, EmployeeRole.CO_OWNER);

    private final Clock clock;
    private final SecurityService securityService;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional(readOnly = true)
    public PagedEmployeeResponse getEmployees(Long companyId, SearchAndPageRequest request) {
        String userId = securityService.getId();
        log.debug("Getting employees for company {} by user {}", companyId, userId);
        validateCompanyMembership(companyId, userId);
        PageRequest pageRequest = PageRequest.of(request.getPageNumber(), request.getPageSize());
        Specification<Employee> spec = EmployeeQueryParamsUtil.createSearchSpecification(companyId, request);
        try {
            Page<Employee> employees = employeeRepository.findAll(spec, pageRequest);
            return EmployeeMapper.INSTANCE.toModel(employees);
        } catch (RuntimeException ex) {
            log.debug("Invalid query or sort parameters for company {}: query='{}', sort='{}'",
                    companyId, request.getQuery(), request.getSort());
            throw new IllegalArgumentException("Invalid query or sort parameters", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeResponse getEmployeeById(Long companyId, Long employeeId) {
        String userId = securityService.getId();
        log.debug("Getting employee {} for company {} by user {}", employeeId, companyId, userId);
        validateCompanyMembership(companyId, userId);
        Employee employee = employeeRepository.findByEmployeeIdAndCompanyCompanyId(employeeId, companyId)
                .orElseThrow(() -> {
                    log.debug("No employee with id {} in company {}", employeeId, companyId);
                    return new ResourceNotFoundException(EMPLOYEE_NOT_FOUND_MESSAGE.formatted(employeeId));
                });
        return EmployeeMapper.INSTANCE.toModel(employee);
    }

    @Override
    @Transactional
    public EmployeeResponse saveEmployee(Long companyId, EmployeeSaveRequest request) {
        String userId = securityService.getId();
        log.debug("Adding employee {} to company {} by user {}", request, companyId, userId);
        validateOwnerOrCoOwner(companyId, userId);
        if (request.getRole() == CompanyRole.OWNER) {
            log.debug("Attempted to assign OWNER role to employee");
            throw new IllegalArgumentException("Cannot assign OWNER role to employee");
        }
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> {
                    log.debug("User {} not found", request.getUserId());
                    return new ResourceNotFoundException(USER_NOT_FOUND_MESSAGE.formatted(request.getUserId()));
                });
        Optional<Employee> existingEmployee =
                employeeRepository.findByCompanyCompanyIdAndUserUserId(companyId, request.getUserId());
        if (existingEmployee.isPresent()) {
            Employee employee = existingEmployee.get();
            if (employee.getStatus() == EmployeeStatus.ACTIVE) {
                log.debug("User {} is already an employee of company {}", request.getUserId(), companyId);
                throw new ConflictException("User %s is already an employee of company %d"
                        .formatted(request.getUserId(), companyId));
            }
            log.debug("Reactivating employee {} in company {}", employee.getEmployeeId(), companyId);
            employee.setStatus(EmployeeStatus.ACTIVE);
            employee.setRole(EmployeeMapper.INSTANCE.toDomain(request.getRole()));
            employee.setAssignedDate(Instant.now(clock));
            employee = employeeRepository.save(employee);
            return EmployeeMapper.INSTANCE.toModel(employee);
        }
        Company company = companyRepository.getReferenceById(companyId);
        Employee employee = Employee.builder()
                .company(company)
                .user(user)
                .role(EmployeeMapper.INSTANCE.toDomain(request.getRole()))
                .status(EmployeeStatus.ACTIVE)
                .assignedDate(Instant.now(clock))
                .build();
        employee = employeeRepository.save(employee);
        log.debug("Saved employee {} with id {} for company {}", employee.getUser().getUserId(),
                employee.getEmployeeId(), companyId);
        return EmployeeMapper.INSTANCE.toModel(employee);
    }

    @Override
    @Transactional
    public EmployeeResponse updateEmployee(Long companyId, Long employeeId, EmployeeUpdateRequest request) {
        String userId = securityService.getId();
        log.debug("Updating employee {} for company {} by user {}", employeeId, companyId, userId);
        validateOwnerOrCoOwner(companyId, userId);
        Employee employee = employeeRepository.findByEmployeeIdAndCompanyCompanyId(employeeId, companyId)
                .orElseThrow(() -> {
                    log.debug("No employee with id {} in company {}", employeeId, companyId);
                    return new ResourceNotFoundException(EMPLOYEE_NOT_FOUND_MESSAGE.formatted(employeeId));
                });
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            log.debug("Employee {} is not active in company {}", employeeId, companyId);
            throw new ResourceNotFoundException(EMPLOYEE_NOT_FOUND_MESSAGE.formatted(employeeId));
        }
        if (employee.getRole() == EmployeeRole.OWNER) {
            log.debug("Attempted to update role of company owner");
            throw new IllegalArgumentException("Cannot update the role of the company owner");
        }
        if (request.getRole() == CompanyRole.OWNER) {
            log.debug("Attempted to assign OWNER role to employee");
            throw new IllegalArgumentException("Cannot assign OWNER role to employee");
        }
        employee.setRole(EmployeeMapper.INSTANCE.toDomain(request.getRole()));
        employee = employeeRepository.save(employee);
        log.debug("Updated employee {} role to {} in company {}", employeeId, request.getRole(), companyId);
        return EmployeeMapper.INSTANCE.toModel(employee);
    }

    @Override
    @Transactional
    public void deleteEmployee(Long companyId, Long employeeId) {
        String userId = securityService.getId();
        log.debug("Deleting employee {} from company {} by user {}", employeeId, companyId, userId);
        validateOwnerOrCoOwner(companyId, userId);
        Employee employee = employeeRepository.findByEmployeeIdAndCompanyCompanyId(employeeId, companyId)
                .orElseThrow(() -> {
                    log.debug("No employee with id {} in company {}", employeeId, companyId);
                    return new ResourceNotFoundException(EMPLOYEE_NOT_FOUND_MESSAGE.formatted(employeeId));
                });
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            log.debug("Employee {} is already inactive in company {}", employeeId, companyId);
            throw new ResourceNotFoundException(EMPLOYEE_NOT_FOUND_MESSAGE.formatted(employeeId));
        }
        if (employee.getRole() == EmployeeRole.OWNER) {
            log.debug("Attempted to remove company owner");
            throw new IllegalArgumentException("Cannot remove the owner of the company");
        }
        employee.setStatus(EmployeeStatus.INACTIVE);
        employeeRepository.save(employee);
        log.debug("Deactivated employee {} from company {}", employeeId, companyId);
    }

    private void validateCompanyMembership(Long companyId, String userId) {
        if (!employeeRepository.existsByCompanyCompanyIdAndUserUserIdAndStatus(
                companyId, userId, EmployeeStatus.ACTIVE)) {
            log.debug("User {} is not an active employee of company {}", userId, companyId);
            throw new ResourceNotFoundException(COMPANY_NOT_FOUND_MESSAGE.formatted(companyId));
        }
    }

    private void validateOwnerOrCoOwner(Long companyId, String userId) {
        if (!employeeRepository.existsByCompanyCompanyIdAndUserUserIdAndRoleInAndStatus(
                companyId, userId, OWNER_AND_CO_OWNER, EmployeeStatus.ACTIVE)) {
            log.debug("User {} is not an active owner or co-owner of company {}", userId, companyId);
            throw new ResourceNotFoundException(COMPANY_NOT_FOUND_MESSAGE.formatted(companyId));
        }
    }

}
