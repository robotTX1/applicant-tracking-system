package hu.robottx.ats.security.service;

import java.util.Collection;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import hu.robottx.ats.domain.CompanyPermission;
import hu.robottx.ats.domain.EmployeeRole;
import hu.robottx.ats.domain.EmployeeStatus;
import hu.robottx.ats.exception.ResourceNotFoundException;
import hu.robottx.ats.repository.EmployeeRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanySecurityService {

    private static final String COMPANY_NOT_FOUND_MESSAGE = "No company with id: %d";

    private final SecurityService securityService;
    private final EmployeeRepository employeeRepository;

    public void validatePermission(Long companyId, CompanyPermission permission) {
        String userId = securityService.getId();
        if (!hasPermission(companyId, userId, permission)) {
            log.debug("User {} does not have permission {} in company {}", userId, permission, companyId);
            throw new ResourceNotFoundException(COMPANY_NOT_FOUND_MESSAGE.formatted(companyId));
        }
    }

    public boolean hasPermission(Long companyId, CompanyPermission permission) {
        return hasPermission(companyId, securityService.getId(), permission);
    }

    public boolean hasPermission(Long companyId, String userId, CompanyPermission permission) {
        Set<EmployeeRole> roles = EmployeeRole.getRolesWith(permission);
        return employeeRepository.existsByCompanyCompanyIdAndUserUserIdAndRoleInAndStatus(
                companyId, userId, roles, EmployeeStatus.ACTIVE);
    }

    public Set<Long> getCompanyIdsWithPermission(CompanyPermission permission) {
        String userId = securityService.getId();
        Set<EmployeeRole> roles = EmployeeRole.getRolesWith(permission);
        return employeeRepository.findCompanyIdsByUserIdAndRolesAndStatus(
                userId, roles, EmployeeStatus.ACTIVE);
    }

    public Set<Long> getCompanyIdsWithRoles(Collection<EmployeeRole> roles) {
        String userId = securityService.getId();
        return employeeRepository.findCompanyIdsByUserIdAndRolesAndStatus(
                userId, roles, EmployeeStatus.ACTIVE);
    }

    public Set<Long> getActiveCompanyIds() {
        String userId = securityService.getId();
        return employeeRepository.findCompanyIdsByUserIdAndStatus(userId, EmployeeStatus.ACTIVE);
    }

}
