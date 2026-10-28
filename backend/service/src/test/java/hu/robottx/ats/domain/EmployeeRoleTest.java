package hu.robottx.ats.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import hu.robottx.ats.UnitTest;

class EmployeeRoleTest extends UnitTest {

    @Test
    @DisplayName("OWNER has all company permissions")
    void ownerHasAllPermissions() {
        for (CompanyPermission permission : CompanyPermission.values()) {
            assertThat(EmployeeRole.OWNER.hasPermission(permission)).isTrue();
        }
    }

    @Test
    @DisplayName("CO_OWNER has all permissions except DELETE_COMPANY")
    void coOwnerPermissions() {
        assertThat(EmployeeRole.CO_OWNER.hasPermission(CompanyPermission.DELETE_COMPANY)).isFalse();
        assertThat(EmployeeRole.CO_OWNER.hasPermission(CompanyPermission.UPDATE_COMPANY)).isTrue();
        assertThat(EmployeeRole.CO_OWNER.hasPermission(CompanyPermission.VIEW_EMPLOYEES)).isTrue();
        assertThat(EmployeeRole.CO_OWNER.hasPermission(CompanyPermission.MANAGE_EMPLOYEES)).isTrue();
        assertThat(EmployeeRole.CO_OWNER.hasPermission(CompanyPermission.MANAGE_JOB_LISTINGS)).isTrue();
        assertThat(EmployeeRole.CO_OWNER.hasPermission(CompanyPermission.VIEW_INTERNAL_JOB_LISTINGS)).isTrue();
        assertThat(EmployeeRole.CO_OWNER.hasPermission(CompanyPermission.CREATE_HR_FEEDBACK)).isTrue();
        assertThat(EmployeeRole.CO_OWNER.hasPermission(CompanyPermission.CREATE_TECHNICAL_FEEDBACK)).isTrue();
        assertThat(EmployeeRole.CO_OWNER.hasPermission(CompanyPermission.MANAGE_EMAIL_TEMPLATES)).isTrue();
        assertThat(EmployeeRole.CO_OWNER.hasPermission(CompanyPermission.VIEW_ALL_APPLICATIONS)).isTrue();
        assertThat(EmployeeRole.CO_OWNER.hasPermission(CompanyPermission.VIEW_TECHNICAL_STAGE_APPLICATIONS)).isTrue();
        assertThat(EmployeeRole.CO_OWNER.hasPermission(CompanyPermission.MANAGE_APPLICATIONS)).isTrue();
    }

    @Test
    @DisplayName("HR_MANAGER has HR related permissions")
    void hrManagerPermissions() {
        assertThat(EmployeeRole.HR_MANAGER.hasPermission(CompanyPermission.UPDATE_COMPANY)).isFalse();
        assertThat(EmployeeRole.HR_MANAGER.hasPermission(CompanyPermission.DELETE_COMPANY)).isFalse();
        assertThat(EmployeeRole.HR_MANAGER.hasPermission(CompanyPermission.MANAGE_EMPLOYEES)).isFalse();
        assertThat(EmployeeRole.HR_MANAGER.hasPermission(CompanyPermission.CREATE_TECHNICAL_FEEDBACK)).isFalse();
        assertThat(EmployeeRole.HR_MANAGER.hasPermission(CompanyPermission.VIEW_EMPLOYEES)).isTrue();
        assertThat(EmployeeRole.HR_MANAGER.hasPermission(CompanyPermission.MANAGE_JOB_LISTINGS)).isTrue();
        assertThat(EmployeeRole.HR_MANAGER.hasPermission(CompanyPermission.VIEW_INTERNAL_JOB_LISTINGS)).isTrue();
        assertThat(EmployeeRole.HR_MANAGER.hasPermission(CompanyPermission.CREATE_HR_FEEDBACK)).isTrue();
        assertThat(EmployeeRole.HR_MANAGER.hasPermission(CompanyPermission.MANAGE_EMAIL_TEMPLATES)).isTrue();
        assertThat(EmployeeRole.HR_MANAGER.hasPermission(CompanyPermission.VIEW_ALL_APPLICATIONS)).isTrue();
        assertThat(EmployeeRole.HR_MANAGER.hasPermission(CompanyPermission.VIEW_TECHNICAL_STAGE_APPLICATIONS)).isTrue();
        assertThat(EmployeeRole.HR_MANAGER.hasPermission(CompanyPermission.MANAGE_APPLICATIONS)).isTrue();
    }

    @Test
    @DisplayName("TECHNICAL_INTERVIEWER has technical interviewer permissions")
    void technicalInterviewerPermissions() {
        assertThat(EmployeeRole.TECHNICAL_INTERVIEWER.hasPermission(CompanyPermission.UPDATE_COMPANY)).isFalse();
        assertThat(EmployeeRole.TECHNICAL_INTERVIEWER.hasPermission(CompanyPermission.DELETE_COMPANY)).isFalse();
        assertThat(EmployeeRole.TECHNICAL_INTERVIEWER.hasPermission(CompanyPermission.MANAGE_EMPLOYEES)).isFalse();
        assertThat(EmployeeRole.TECHNICAL_INTERVIEWER.hasPermission(CompanyPermission.MANAGE_JOB_LISTINGS)).isFalse();
        assertThat(EmployeeRole.TECHNICAL_INTERVIEWER
                .hasPermission(CompanyPermission.VIEW_INTERNAL_JOB_LISTINGS)).isFalse();
        assertThat(EmployeeRole.TECHNICAL_INTERVIEWER.hasPermission(CompanyPermission.CREATE_HR_FEEDBACK)).isFalse();
        assertThat(EmployeeRole.TECHNICAL_INTERVIEWER.hasPermission(CompanyPermission.MANAGE_EMAIL_TEMPLATES)).isFalse();
        assertThat(EmployeeRole.TECHNICAL_INTERVIEWER.hasPermission(CompanyPermission.VIEW_ALL_APPLICATIONS)).isFalse();
        assertThat(EmployeeRole.TECHNICAL_INTERVIEWER.hasPermission(CompanyPermission.MANAGE_APPLICATIONS)).isFalse();
        assertThat(EmployeeRole.TECHNICAL_INTERVIEWER.hasPermission(CompanyPermission.VIEW_EMPLOYEES)).isTrue();
        assertThat(EmployeeRole.TECHNICAL_INTERVIEWER
                .hasPermission(CompanyPermission.CREATE_TECHNICAL_FEEDBACK)).isTrue();
        assertThat(EmployeeRole.TECHNICAL_INTERVIEWER
                .hasPermission(CompanyPermission.VIEW_TECHNICAL_STAGE_APPLICATIONS)).isTrue();
    }

    @Test
    @DisplayName("getRolesWith returns expected role sets")
    void getRolesWithPermissions() {
        assertThat(EmployeeRole.getRolesWith(CompanyPermission.DELETE_COMPANY))
                .containsExactly(EmployeeRole.OWNER);
        assertThat(EmployeeRole.getRolesWith(CompanyPermission.UPDATE_COMPANY))
                .containsExactlyInAnyOrder(EmployeeRole.OWNER, EmployeeRole.CO_OWNER);
        assertThat(EmployeeRole.getRolesWith(CompanyPermission.MANAGE_EMPLOYEES))
                .containsExactlyInAnyOrder(EmployeeRole.OWNER, EmployeeRole.CO_OWNER);
        assertThat(EmployeeRole.getRolesWith(CompanyPermission.MANAGE_JOB_LISTINGS))
                .containsExactlyInAnyOrder(EmployeeRole.OWNER, EmployeeRole.CO_OWNER, EmployeeRole.HR_MANAGER);
        assertThat(EmployeeRole.getRolesWith(CompanyPermission.VIEW_INTERNAL_JOB_LISTINGS))
                .containsExactlyInAnyOrder(EmployeeRole.OWNER, EmployeeRole.CO_OWNER, EmployeeRole.HR_MANAGER);
        assertThat(EmployeeRole.getRolesWith(CompanyPermission.VIEW_EMPLOYEES))
                .containsExactlyInAnyOrder(EmployeeRole.values());
        assertThat(EmployeeRole.getRolesWith(CompanyPermission.CREATE_HR_FEEDBACK))
                .containsExactlyInAnyOrder(EmployeeRole.OWNER, EmployeeRole.CO_OWNER, EmployeeRole.HR_MANAGER);
        assertThat(EmployeeRole.getRolesWith(CompanyPermission.CREATE_TECHNICAL_FEEDBACK))
                .containsExactlyInAnyOrder(EmployeeRole.OWNER, EmployeeRole.CO_OWNER,
                        EmployeeRole.TECHNICAL_INTERVIEWER);
        assertThat(EmployeeRole.getRolesWith(CompanyPermission.MANAGE_EMAIL_TEMPLATES))
                .containsExactlyInAnyOrder(EmployeeRole.OWNER, EmployeeRole.CO_OWNER, EmployeeRole.HR_MANAGER);
        assertThat(EmployeeRole.getRolesWith(CompanyPermission.VIEW_ALL_APPLICATIONS))
                .containsExactlyInAnyOrder(EmployeeRole.OWNER, EmployeeRole.CO_OWNER, EmployeeRole.HR_MANAGER);
        assertThat(EmployeeRole.getRolesWith(CompanyPermission.VIEW_TECHNICAL_STAGE_APPLICATIONS))
                .containsExactlyInAnyOrder(EmployeeRole.values());
        assertThat(EmployeeRole.getRolesWith(CompanyPermission.MANAGE_APPLICATIONS))
                .containsExactlyInAnyOrder(EmployeeRole.OWNER, EmployeeRole.CO_OWNER, EmployeeRole.HR_MANAGER);
    }

    @Test
    @DisplayName("getPermissions returns unmodifiable set")
    void getPermissionsUnmodifiable() {
        Set<CompanyPermission> permissions = EmployeeRole.OWNER.getPermissions();
        assertThat(permissions).isNotEmpty();
    }

}
