import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import {
  CompanyEmployeeStatus,
  CompanyResponse,
  CompanyRole,
  EmployeeResponse,
  EmployeesService,
} from '../../api';
import { AddMemberDialog } from '../../components/add-member-dialog/add-member-dialog';
import { CompanyRoleService } from '../../services/company-role.service';
import { CompanyStateService } from '../../services/company-state.service';
import { CompanyTeam, compareMembers } from './company-team';

const company: CompanyResponse = {
  id: 7,
  name: 'Acme',
  foundingDate: '',
  websiteUrl: '',
  description: '',
};

const member = (
  id: number,
  role: CompanyRole,
  firstName: string,
  status = CompanyEmployeeStatus.Active,
): EmployeeResponse => ({
  id,
  companyId: 7,
  userId: `u${id}`,
  firstName,
  lastName: 'Doe',
  email: `${firstName.toLowerCase()}@example.com`,
  role,
  status,
  assignedDate: '',
});

describe('CompanyTeam', () => {
  const owner = member(1, CompanyRole.Owner, 'Olivia');
  const coOwner = member(2, CompanyRole.CoOwner, 'Carl');
  const hr = member(3, CompanyRole.HrManager, 'Hanna');
  const tech = member(4, CompanyRole.TechnicalInterviewer, 'Tom');
  const former = member(5, CompanyRole.HrManager, 'Fred', CompanyEmployeeStatus.Inactive);

  let employeesService: {
    getEmployees: ReturnType<typeof vi.fn>;
    updateEmployee: ReturnType<typeof vi.fn>;
    deleteEmployee: ReturnType<typeof vi.fn>;
  };
  let me: EmployeeResponse;
  let dialogResult: Subject<unknown>;
  let dialog: { open: ReturnType<typeof vi.fn> };
  let snackBar: { open: ReturnType<typeof vi.fn> };
  let roleService: {
    getEmployee: () => Promise<EmployeeResponse>;
    invalidate: ReturnType<typeof vi.fn>;
  };
  let companyState: {
    companies: ReturnType<typeof signal<CompanyResponse[]>>;
    refresh: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    me = owner;
    employeesService = {
      getEmployees: vi.fn().mockReturnValue(
        of({
          content: [tech, former, hr, owner, coOwner],
          page: { size: 100, number: 0, totalElements: 5, totalPages: 1 },
        }),
      ),
      updateEmployee: vi
        .fn()
        .mockImplementation((_c: number, id: number, req: { role: CompanyRole }) =>
          of({ ...[owner, coOwner, hr, tech].find((m) => m.id === id)!, role: req.role }),
        ),
      deleteEmployee: vi.fn().mockReturnValue(of(undefined)),
    };
    dialogResult = new Subject();
    dialog = { open: vi.fn().mockReturnValue({ afterClosed: () => dialogResult }) };
    snackBar = { open: vi.fn() };
    roleService = { getEmployee: () => Promise.resolve(me), invalidate: vi.fn() };
    companyState = { companies: signal([company]), refresh: vi.fn().mockResolvedValue(undefined) };

    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [
            { path: 'companies/:companyId/team', component: CompanyTeam },
            { path: 'companies', children: [] },
          ],
          withComponentInputBinding(),
        ),
        { provide: EmployeesService, useValue: employeesService },
        { provide: CompanyStateService, useValue: companyState },
        { provide: CompanyRoleService, useValue: roleService },
        { provide: MatDialog, useValue: dialog },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
  });

  async function render(url = '/companies/7/team') {
    const harness = await RouterTestingHarness.create(url);
    await harness.fixture.whenStable();
    return {
      harness,
      el: harness.routeNativeElement as HTMLElement,
      component: harness.routeDebugElement!.componentInstance as CompanyTeam,
    };
  }

  const rows = (el: HTMLElement) => [
    ...el.querySelectorAll<HTMLElement>('ul[aria-label="Team members"] .member-row'),
  ];

  it('lists active members owner first, then by role', async () => {
    const { el } = await render();
    expect(rows(el).map((r) => r.querySelector('.member-name')?.textContent?.trim())).toEqual([
      'Olivia Doe You',
      'Carl Doe',
      'Hanna Doe',
      'Tom Doe',
    ]);
    expect(employeesService.getEmployees).toHaveBeenCalledWith(7, { pageSize: 100 });
  });

  it('lets owners change roles and remove anyone but the owner', async () => {
    const { el } = await render();
    const [ownerRow, coOwnerRow] = rows(el);
    expect(ownerRow.querySelector('.role-chip')?.textContent).toContain('Owner');
    expect(ownerRow.querySelector('.remove-button')).toBeNull();
    expect(coOwnerRow.querySelector('.role-button')?.textContent).toContain('Co-owner');
    expect(coOwnerRow.querySelector('.remove-button')).not.toBeNull();
    expect(el.textContent).toContain('Add member');
  });

  it('is read-only for HR managers and technical interviewers', async () => {
    me = hr;
    const { el } = await render();
    expect(el.querySelector('.role-button')).toBeNull();
    expect(el.querySelector('.remove-button')).toBeNull();
    expect(el.textContent).not.toContain('Add member');
    expect(el.textContent).toContain('Owners and co-owners manage the team');
  });

  it('changes a role in place', async () => {
    const { el, harness, component } = await render();
    component.changeRole(hr, CompanyRole.TechnicalInterviewer);
    await harness.fixture.whenStable();
    expect(dialog.open).not.toHaveBeenCalled();
    expect(employeesService.updateEmployee).toHaveBeenCalledWith(7, 3, {
      role: CompanyRole.TechnicalInterviewer,
    });
    expect(rows(el)[2].textContent).toContain('Technical interviewer');
    expect(snackBar.open).toHaveBeenCalledWith(
      'Hanna Doe is now Technical interviewer.',
      'Close',
      expect.anything(),
    );
  });

  it('asks before co-owners demote themselves, and refreshes their role', async () => {
    me = coOwner;
    const { harness, component } = await render();
    component.changeRole(coOwner, CompanyRole.HrManager);
    expect(dialog.open.mock.lastCall![1].data.title).toBe('Change your own role?');
    expect(employeesService.updateEmployee).not.toHaveBeenCalled();
    dialogResult.next(true);
    await harness.fixture.whenStable();
    expect(employeesService.updateEmployee).toHaveBeenCalledWith(7, 2, {
      role: CompanyRole.HrManager,
    });
    expect(roleService.invalidate).toHaveBeenCalledWith(7);
  });

  it('removes a member after confirmation and shows them under former members', async () => {
    const { el, harness, component } = await render();
    component.removeMember(tech);
    expect(dialog.open.mock.lastCall![1].data.title).toBe('Remove Tom Doe?');
    dialogResult.next(true);
    await harness.fixture.whenStable();
    expect(employeesService.deleteEmployee).toHaveBeenCalledWith(7, 4);
    expect(rows(el).length).toBe(3);
    expect(el.textContent).toContain('Show former members (2)');
  });

  it('reports a failed removal', async () => {
    employeesService.deleteEmployee.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { details: 'Nope' } })),
    );
    const { el, harness, component } = await render();
    component.removeMember(tech);
    dialogResult.next(true);
    await harness.fixture.whenStable();
    expect(snackBar.open).toHaveBeenCalledWith('Nope', 'Close', expect.anything());
    expect(rows(el).length).toBe(4);
  });

  it('sends members who leave back to the company list', async () => {
    me = coOwner;
    const { harness, component } = await render();
    component.removeMember(coOwner);
    expect(dialog.open.mock.lastCall![1].data.title).toBe('Leave this company?');
    dialogResult.next(true);
    await harness.fixture.whenStable();
    expect(companyState.refresh).toHaveBeenCalled();
    expect(TestBed.inject(Router).url).toBe('/companies');
  });

  it('adds a member returned by the dialog', async () => {
    const { el, harness, component } = await render();
    component.addMember();
    expect(dialog.open).toHaveBeenCalledWith(
      AddMemberDialog,
      expect.objectContaining({
        data: expect.objectContaining({
          companyId: 7,
          memberUserIds: new Set(['u1', 'u2', 'u3', 'u4']),
        }),
      }),
    );
    dialogResult.next(member(9, CompanyRole.HrManager, 'Nina'));
    await harness.fixture.whenStable();
    expect(rows(el).length).toBe(5);
  });

  it('hides former members behind a toggle', async () => {
    const { el, harness, component } = await render();
    expect(el.querySelector('.former')).toBeNull();
    component.showFormer.set(true);
    await harness.fixture.whenStable();
    expect(el.querySelector('.former')?.textContent).toContain('Former HR manager');
  });

  it('shows not found for a company the user is not in', async () => {
    const { el } = await render('/companies/99/team');
    expect(el.textContent).toContain('Company not found');
  });

  it('compareMembers orders by role then last and first name', () => {
    const list = [tech, hr, owner].sort(compareMembers);
    expect(list.map((m) => m.id)).toEqual([1, 3, 4]);
  });
});
