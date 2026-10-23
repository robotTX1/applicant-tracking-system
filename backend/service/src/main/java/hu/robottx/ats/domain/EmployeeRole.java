package hu.robottx.ats.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import lombok.Getter;

public enum EmployeeRole {
    OWNER(EnumSet.allOf(CompanyPermission.class)),
    CO_OWNER(EnumSet.of(
            CompanyPermission.UPDATE_COMPANY,
            CompanyPermission.VIEW_EMPLOYEES,
            CompanyPermission.MANAGE_EMPLOYEES,
            CompanyPermission.MANAGE_JOB_LISTINGS,
            CompanyPermission.VIEW_INTERNAL_JOB_LISTINGS,
            CompanyPermission.CREATE_HR_FEEDBACK,
            CompanyPermission.CREATE_TECHNICAL_FEEDBACK,
            CompanyPermission.MANAGE_EMAIL_TEMPLATES,
            CompanyPermission.VIEW_ALL_APPLICATIONS,
            CompanyPermission.VIEW_TECHNICAL_STAGE_APPLICATIONS
    )),
    HR_MANAGER(EnumSet.of(
            CompanyPermission.VIEW_EMPLOYEES,
            CompanyPermission.MANAGE_JOB_LISTINGS,
            CompanyPermission.VIEW_INTERNAL_JOB_LISTINGS,
            CompanyPermission.CREATE_HR_FEEDBACK,
            CompanyPermission.MANAGE_EMAIL_TEMPLATES,
            CompanyPermission.VIEW_ALL_APPLICATIONS,
            CompanyPermission.VIEW_TECHNICAL_STAGE_APPLICATIONS
    )),
    TECHNICAL_INTERVIEWER(EnumSet.of(
            CompanyPermission.VIEW_EMPLOYEES,
            CompanyPermission.CREATE_TECHNICAL_FEEDBACK,
            CompanyPermission.VIEW_TECHNICAL_STAGE_APPLICATIONS
    ));

    private static final Map<CompanyPermission, Set<EmployeeRole>> ROLES_BY_PERMISSION;

    static {
        Map<CompanyPermission, Set<EmployeeRole>> map = new EnumMap<>(CompanyPermission.class);
        for (CompanyPermission permission : CompanyPermission.values()) {
            EnumSet<EmployeeRole> roles = EnumSet.noneOf(EmployeeRole.class);
            for (EmployeeRole role : values()) {
                if (role.hasPermission(permission)) {
                    roles.add(role);
                }
            }
            map.put(permission, Collections.unmodifiableSet(roles));
        }
        ROLES_BY_PERMISSION = Collections.unmodifiableMap(map);
    }

    @Getter
    private final Set<CompanyPermission> permissions;

    EmployeeRole(Set<CompanyPermission> permissions) {
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public boolean hasPermission(CompanyPermission permission) {
        return permissions.contains(permission);
    }

    public static Set<EmployeeRole> getRolesWith(CompanyPermission permission) {
        return ROLES_BY_PERMISSION.get(permission);
    }

}
