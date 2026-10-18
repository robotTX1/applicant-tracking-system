package hu.robottx.ats.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import hu.robottx.ats.UnitTest;
import hu.robottx.ats.domain.CompanyPermission;
import hu.robottx.ats.domain.EmployeeRole;
import hu.robottx.ats.domain.EmployeeStatus;
import hu.robottx.ats.exception.ResourceNotFoundException;
import hu.robottx.ats.repository.EmployeeRepository;

class CompanySecurityServiceTest extends UnitTest {

    private static final String USER_ID = "test-user-123";
    private static final Long COMPANY_ID = 1L;

    @Mock
    private SecurityService securityService;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private CompanySecurityService companySecurityService;

    @Test
    @DisplayName("validatePermission passes when user has required permission")
    void validatePermissionSuccess() {
        when(securityService.getId()).thenReturn(USER_ID);
        Set<EmployeeRole> roles = EmployeeRole.getRolesWith(CompanyPermission.MANAGE_JOB_LISTINGS);
        when(employeeRepository.existsByCompanyCompanyIdAndUserUserIdAndRoleInAndStatus(
                COMPANY_ID, USER_ID, roles, EmployeeStatus.ACTIVE)).thenReturn(true);
        companySecurityService.validatePermission(COMPANY_ID, CompanyPermission.MANAGE_JOB_LISTINGS);
        verify(securityService).getId();
        verify(employeeRepository).existsByCompanyCompanyIdAndUserUserIdAndRoleInAndStatus(
                COMPANY_ID, USER_ID, roles, EmployeeStatus.ACTIVE);
    }

    @Test
    @DisplayName("validatePermission throws ResourceNotFoundException when user lacks permission")
    void validatePermissionThrowsNotFound() {
        when(securityService.getId()).thenReturn(USER_ID);
        Set<EmployeeRole> roles = EmployeeRole.getRolesWith(CompanyPermission.DELETE_COMPANY);
        when(employeeRepository.existsByCompanyCompanyIdAndUserUserIdAndRoleInAndStatus(
                COMPANY_ID, USER_ID, roles, EmployeeStatus.ACTIVE)).thenReturn(false);
        assertThatThrownBy(
                () -> companySecurityService.validatePermission(COMPANY_ID, CompanyPermission.DELETE_COMPANY))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No company with id: 1");
    }

    @Test
    @DisplayName("hasPermission returns true when user has permission")
    void hasPermissionTrue() {
        when(securityService.getId()).thenReturn(USER_ID);
        Set<EmployeeRole> roles = EmployeeRole.getRolesWith(CompanyPermission.VIEW_EMPLOYEES);
        when(employeeRepository.existsByCompanyCompanyIdAndUserUserIdAndRoleInAndStatus(
                COMPANY_ID, USER_ID, roles, EmployeeStatus.ACTIVE)).thenReturn(true);
        boolean result = companySecurityService.hasPermission(COMPANY_ID, CompanyPermission.VIEW_EMPLOYEES);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("hasPermission returns false when user lacks permission")
    void hasPermissionFalse() {
        when(securityService.getId()).thenReturn(USER_ID);
        Set<EmployeeRole> roles = EmployeeRole.getRolesWith(CompanyPermission.MANAGE_EMPLOYEES);
        when(employeeRepository.existsByCompanyCompanyIdAndUserUserIdAndRoleInAndStatus(
                COMPANY_ID, USER_ID, roles, EmployeeStatus.ACTIVE)).thenReturn(false);
        boolean result = companySecurityService.hasPermission(COMPANY_ID, CompanyPermission.MANAGE_EMPLOYEES);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("getCompanyIdsWithPermission returns company IDs for permission")
    void getCompanyIdsWithPermission() {
        when(securityService.getId()).thenReturn(USER_ID);
        Set<EmployeeRole> roles = EmployeeRole.getRolesWith(CompanyPermission.VIEW_INTERNAL_JOB_LISTINGS);
        Set<Long> expectedIds = Set.of(1L, 2L);
        when(employeeRepository.findCompanyIdsByUserIdAndRolesAndStatus(
                USER_ID, roles, EmployeeStatus.ACTIVE)).thenReturn(expectedIds);
        Set<Long> result = companySecurityService.getCompanyIdsWithPermission(
                CompanyPermission.VIEW_INTERNAL_JOB_LISTINGS);
        assertThat(result).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("getCompanyIdsWithRoles returns company IDs for given roles")
    void getCompanyIdsWithRoles() {
        when(securityService.getId()).thenReturn(USER_ID);
        List<EmployeeRole> roles = List.of(EmployeeRole.OWNER);
        Set<Long> expectedIds = Set.of(3L);
        when(employeeRepository.findCompanyIdsByUserIdAndRolesAndStatus(
                USER_ID, roles, EmployeeStatus.ACTIVE)).thenReturn(expectedIds);
        Set<Long> result = companySecurityService.getCompanyIdsWithRoles(roles);
        assertThat(result).containsExactly(3L);
    }

    @Test
    @DisplayName("getActiveCompanyIds returns all active company IDs for user")
    void getActiveCompanyIds() {
        when(securityService.getId()).thenReturn(USER_ID);
        Set<Long> expectedIds = Set.of(1L, 2L, 3L);
        when(employeeRepository.findCompanyIdsByUserIdAndStatus(
                USER_ID, EmployeeStatus.ACTIVE)).thenReturn(expectedIds);
        Set<Long> result = companySecurityService.getActiveCompanyIds();
        assertThat(result).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

}
