import { TestBed } from '@angular/core/testing';
import Keycloak from 'keycloak-js';
import { of, throwError } from 'rxjs';
import { CompanyEmployeeStatus, CompanyRole, EmployeesService } from '../api';
import {
  canGiveHrFeedback,
  canGiveTechnicalFeedback,
  canManageJobs,
  CompanyRoleService,
} from './company-role.service';

describe('CompanyRoleService', () => {
  let employeesService: { getEmployees: ReturnType<typeof vi.fn> };
  let keycloak: { subject?: string };

  const employee = (role: CompanyRole, status = CompanyEmployeeStatus.Active) => ({
    content: [{ id: 1, companyId: 7, userId: 'sub-1', role, status }],
    page: { size: 1, number: 0, totalElements: 1, totalPages: 1 },
  });

  beforeEach(() => {
    keycloak = { subject: 'sub-1' };
    employeesService = {
      getEmployees: vi.fn().mockReturnValue(of(employee(CompanyRole.HrManager))),
    };
    TestBed.configureTestingModule({
      providers: [
        { provide: Keycloak, useValue: keycloak },
        { provide: EmployeesService, useValue: employeesService },
      ],
    });
  });

  const service = () => TestBed.inject(CompanyRoleService);

  it("looks up the user's employee record by Keycloak subject and caches it", async () => {
    expect(await service().getRole(7)).toBe(CompanyRole.HrManager);
    expect(await service().getRole(7)).toBe(CompanyRole.HrManager);
    expect(employeesService.getEmployees).toHaveBeenCalledTimes(1);
    expect(employeesService.getEmployees).toHaveBeenCalledWith(7, {
      query: "userId=='sub-1'",
      pageSize: 1,
    });
  });

  it('returns null for inactive or missing employees', async () => {
    employeesService.getEmployees.mockReturnValueOnce(
      of(employee(CompanyRole.Owner, CompanyEmployeeStatus.Inactive)),
    );
    expect(await service().getRole(7)).toBeNull();
    employeesService.getEmployees.mockReturnValueOnce(
      of({ content: [], page: { size: 1, number: 0, totalElements: 0, totalPages: 0 } }),
    );
    expect(await service().getRole(8)).toBeNull();
  });

  it('returns null without a signed-in user', async () => {
    keycloak.subject = undefined;
    expect(await service().getRole(7)).toBeNull();
    expect(employeesService.getEmployees).not.toHaveBeenCalled();
  });

  it('does not cache failures', async () => {
    employeesService.getEmployees.mockReturnValueOnce(throwError(() => new Error('down')));
    expect(await service().getRole(7)).toBeNull();
    expect(await service().getRole(7)).toBe(CompanyRole.HrManager);
  });

  it('canManageJobs allows owners, co-owners and HR managers only', () => {
    expect(canManageJobs(CompanyRole.Owner)).toBe(true);
    expect(canManageJobs(CompanyRole.CoOwner)).toBe(true);
    expect(canManageJobs(CompanyRole.HrManager)).toBe(true);
    expect(canManageJobs(CompanyRole.TechnicalInterviewer)).toBe(false);
    expect(canManageJobs(null)).toBe(false);
  });

  it('getEmployee returns the cached employee record', async () => {
    const employee = await service().getEmployee(7);
    expect(employee?.id).toBe(1);
    expect(await service().getRole(7)).toBe(CompanyRole.HrManager);
    expect(employeesService.getEmployees).toHaveBeenCalledTimes(1);
  });

  it('feedback permissions follow the backend roles', () => {
    expect(canGiveHrFeedback(CompanyRole.HrManager)).toBe(true);
    expect(canGiveHrFeedback(CompanyRole.TechnicalInterviewer)).toBe(false);
    expect(canGiveTechnicalFeedback(CompanyRole.TechnicalInterviewer)).toBe(true);
    expect(canGiveTechnicalFeedback(CompanyRole.Owner)).toBe(true);
    expect(canGiveTechnicalFeedback(CompanyRole.HrManager)).toBe(false);
  });
});
