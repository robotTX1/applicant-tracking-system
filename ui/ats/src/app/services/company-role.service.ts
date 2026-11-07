import { effect, inject, Injectable } from '@angular/core';
import Keycloak from 'keycloak-js';
import { KEYCLOAK_EVENT_SIGNAL, KeycloakEventType } from 'keycloak-angular';
import { firstValueFrom, map } from 'rxjs';
import { CompanyEmployeeStatus, CompanyRole, EmployeeResponse, EmployeesService } from '../api';
import { eq } from '../utils/rsql';

/** Roles that may create, edit, publish, close and delete job listings (MANAGE_JOB_LISTINGS). */
const JOB_MANAGER_ROLES: ReadonlySet<CompanyRole> = new Set([
  CompanyRole.Owner,
  CompanyRole.CoOwner,
  CompanyRole.HrManager,
]);

export function canManageJobs(role: CompanyRole | null | undefined): boolean {
  return !!role && JOB_MANAGER_ROLES.has(role);
}

/** Moving applications between stages (MANAGE_APPLICATIONS) — same roles as job management. */
export function canManageApplications(role: CompanyRole | null | undefined): boolean {
  return canManageJobs(role);
}

/** CREATE_HR_FEEDBACK: owners, co-owners and HR managers. */
export function canGiveHrFeedback(role: CompanyRole | null | undefined): boolean {
  return canManageJobs(role);
}

/** CREATE_TECHNICAL_FEEDBACK: owners, co-owners and technical interviewers. */
export function canGiveTechnicalFeedback(role: CompanyRole | null | undefined): boolean {
  return (
    role === CompanyRole.Owner ||
    role === CompanyRole.CoOwner ||
    role === CompanyRole.TechnicalInterviewer
  );
}

/**
 * Looks up the signed-in user's role in a company (via the employee list filtered by their
 * Keycloak subject) and caches it per company for the session. Null means "not an active
 * employee" or "couldn't tell"; the backend still enforces permissions either way.
 */
@Injectable({ providedIn: 'root' })
export class CompanyRoleService {
  private readonly keycloak = inject(Keycloak);
  private readonly keycloakSignal = inject(KEYCLOAK_EVENT_SIGNAL, { optional: true });
  private readonly employeesService = inject(EmployeesService);
  private readonly cache = new Map<number, Promise<EmployeeResponse | null>>();

  constructor() {
    if (this.keycloakSignal) {
      effect(() => {
        if (this.keycloakSignal?.()?.type === KeycloakEventType.AuthLogout) this.cache.clear();
      });
    }
  }

  getRole(companyId: number): Promise<CompanyRole | null> {
    return this.getEmployee(companyId).then((employee) => employee?.role ?? null);
  }

  /** Forget the cached record, e.g. after the user's own role changed. */
  invalidate(companyId: number): void {
    this.cache.delete(companyId);
  }

  /** The signed-in user's active employee record in the company, or null. */
  getEmployee(companyId: number): Promise<EmployeeResponse | null> {
    const userId = this.keycloak.subject;
    if (!userId) return Promise.resolve(null);

    let employee = this.cache.get(companyId);
    if (!employee) {
      employee = firstValueFrom(
        this.employeesService
          .getEmployees(companyId, { query: eq('userId', userId), pageSize: 1 })
          .pipe(
            map((res) => {
              const found = res.content[0];
              return found?.status === CompanyEmployeeStatus.Active ? found : null;
            }),
          ),
      ).catch(() => {
        // Don't cache failures, so the next page visit retries
        this.cache.delete(companyId);
        return null;
      });
      this.cache.set(companyId, employee);
    }
    return employee;
  }
}
