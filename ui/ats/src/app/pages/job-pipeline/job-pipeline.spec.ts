import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import {
  ApplicationsService,
  CompanyEmployeeStatus,
  CompanyResponse,
  CompanyRole,
  EmployeeResponse,
  EmployeesService,
  JobApplicationResponse,
  JobApplicationStatus as S,
  JobListingResponse,
  JobListingsService,
  JobListingStatus,
} from '../../api';
import { ApplicationPanel } from '../../components/application-panel/application-panel';
import { ConfirmDialog } from '../../components/confirm-dialog/confirm-dialog';
import { CompanyRoleService } from '../../services/company-role.service';
import { CompanyStateService } from '../../services/company-state.service';
import { PIPELINE_COLUMNS, PipelineColumnId } from '../../utils/pipeline';
import { JobPipeline } from './job-pipeline';

const company: CompanyResponse = {
  id: 7,
  name: 'Acme',
  foundingDate: '',
  websiteUrl: '',
  description: '',
};

const job: JobListingResponse = {
  id: 5,
  companyId: 7,
  company,
  title: 'Java Developer',
  location: 'Budapest',
  description: '',
  aiReviewEnabled: true,
  status: JobListingStatus.Published,
  createdAt: '',
  updatedAt: '',
};

const app = (id: number, status: S, extra: Partial<JobApplicationResponse> = {}) =>
  ({
    id,
    jobListingId: 5,
    userId: `u${id}`,
    applicantFirstName: 'Ann',
    applicantLastName: `Smith${id}`,
    applicantEmail: `ann${id}@example.com`,
    cvDocumentId: id,
    status,
    createdAt: `2026-11-0${id}T10:00:00Z`,
    updatedAt: '',
    ...extra,
  }) as JobApplicationResponse;

const employee = (role: CompanyRole): EmployeeResponse => ({
  id: 1,
  companyId: 7,
  userId: 'me',
  firstName: 'Me',
  lastName: 'Myself',
  email: 'me@example.com',
  role,
  status: CompanyEmployeeStatus.Active,
  assignedDate: '',
});

const column = (id: PipelineColumnId) => PIPELINE_COLUMNS.find((c) => c.id === id)!;

describe('JobPipeline', () => {
  let applicationsService: {
    getJobListingApplications: ReturnType<typeof vi.fn>;
    updateApplicationStatus: ReturnType<typeof vi.fn>;
  };
  let me: EmployeeResponse;
  let dialogResult: Subject<boolean | undefined>;
  let dialog: { open: ReturnType<typeof vi.fn> };
  let snackBar: { open: ReturnType<typeof vi.fn> };

  const applications = [
    app(1, S.AiReviewed, { aiScore: 82 }),
    app(2, S.Applied),
    app(3, S.HrInterviewScheduled),
    app(4, S.Offer),
  ];

  beforeEach(() => {
    me = employee(CompanyRole.HrManager);
    applicationsService = {
      getJobListingApplications: vi.fn().mockReturnValue(
        of({
          content: applications,
          page: { size: 100, number: 0, totalElements: 4, totalPages: 1 },
        }),
      ),
      updateApplicationStatus: vi
        .fn()
        .mockImplementation((id: number, req: { status: S }) =>
          of({ ...applications.find((a) => a.id === id)!, status: req.status }),
        ),
    };
    dialogResult = new Subject();
    dialog = { open: vi.fn().mockReturnValue({ afterClosed: () => dialogResult }) };
    snackBar = { open: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [{ path: 'companies/:companyId/jobs/:jobId', component: JobPipeline }],
          withComponentInputBinding(),
        ),
        { provide: ApplicationsService, useValue: applicationsService },
        { provide: JobListingsService, useValue: { getJobListing: () => of(job) } },
        {
          provide: EmployeesService,
          useValue: {
            getEmployees: () =>
              of({
                content: [me],
                page: { size: 100, number: 0, totalElements: 1, totalPages: 1 },
              }),
          },
        },
        { provide: CompanyStateService, useValue: { companies: signal([company]) } },
        { provide: CompanyRoleService, useValue: { getEmployee: () => Promise.resolve(me) } },
        { provide: MatDialog, useValue: dialog },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
  });

  async function render(url = '/companies/7/jobs/5') {
    const harness = await RouterTestingHarness.create(url);
    await harness.fixture.whenStable();
    return {
      harness,
      el: harness.routeNativeElement as HTMLElement,
      component: harness.routeDebugElement!.componentInstance as JobPipeline,
    };
  }

  const rowEls = (el: HTMLElement) => [...el.querySelectorAll<HTMLElement>('tr.applicant-row')];
  const names = (el: HTMLElement) =>
    rowEls(el).map((r) => r.querySelector('.applicant-name')?.textContent?.trim());
  const stageOf = (el: HTMLElement, name: string) =>
    rowEls(el)
      .find((r) => r.querySelector('.applicant-name')?.textContent?.trim() === name)
      ?.querySelector('.stage-chip')
      ?.textContent?.trim();
  const tabLabels = (el: HTMLElement) =>
    [...el.querySelectorAll('mat-chip-option')].map((c) =>
      c.textContent?.replace(/\s+/g, ' ').trim(),
    );

  it('lists every applicant, best AI score first, with stage tabs and counts', async () => {
    const { el } = await render();
    expect(names(el)).toEqual(['Ann Smith1', 'Ann Smith2', 'Ann Smith3', 'Ann Smith4']);
    expect(tabLabels(el)).toEqual([
      'All 4',
      'New 2',
      'HR 1',
      'Technical 0',
      'Offer 1',
      'Declined 0',
    ]);
    expect(stageOf(el, 'Ann Smith3')).toBe('HR interview scheduled');
    expect(rowEls(el)[0].querySelector('.ai-score')?.textContent).toContain('82');
    expect(rowEls(el)[1].textContent).toContain('Reviewing'); // APPLIED with AI review on
  });

  it('filters by ?stage= and sorts by ?sort=&dir=', async () => {
    const { el } = await render('/companies/7/jobs/5?stage=new&sort=createdAt&dir=desc');
    expect(names(el)).toEqual(['Ann Smith2', 'Ann Smith1']);
  });

  it('shows an empty state for a stage without candidates', async () => {
    const { el } = await render('/companies/7/jobs/5?stage=technical');
    expect(el.textContent).toContain('No applicants are in the Technical stage');
  });

  it('pages through all applications 100 at a time', async () => {
    applicationsService.getJobListingApplications
      .mockReturnValueOnce(
        of({
          content: [applications[0]],
          page: { size: 100, number: 0, totalElements: 2, totalPages: 2 },
        }),
      )
      .mockReturnValueOnce(
        of({
          content: [applications[2]],
          page: { size: 100, number: 1, totalElements: 2, totalPages: 2 },
        }),
      );
    const { el } = await render();
    expect(applicationsService.getJobListingApplications).toHaveBeenCalledTimes(2);
    expect(applicationsService.getJobListingApplications).toHaveBeenLastCalledWith(5, {
      sort: 'createdAt,asc',
      pageNumber: 1,
      pageSize: 100,
    });
    expect(rowEls(el).length).toBe(2);
  });

  it('moves without confirmation between interview stages', async () => {
    const { el, harness, component } = await render();
    await component.move(applications[0], column('hr-scheduled'));
    await harness.fixture.whenStable();
    expect(dialog.open).not.toHaveBeenCalled();
    expect(applicationsService.updateApplicationStatus).toHaveBeenCalledWith(1, {
      status: S.HrInterviewScheduled,
    });
    expect(stageOf(el, 'Ann Smith1')).toBe('HR interview scheduled');
  });

  it('confirms before an offer, since it emails the candidate', async () => {
    const { el, harness, component } = await render();
    const moved = component.move(applications[2], column('offer'));
    expect(dialog.open).toHaveBeenCalledWith(
      ConfirmDialog,
      expect.objectContaining({
        data: expect.objectContaining({ title: 'Make an offer to Ann Smith3?' }),
      }),
    );
    dialogResult.next(true);
    dialogResult.complete();
    await moved;
    await harness.fixture.whenStable();
    expect(applicationsService.updateApplicationStatus).toHaveBeenCalledWith(3, {
      status: S.Offer,
    });
    expect(stageOf(el, 'Ann Smith3')).toBe('lock Offer'); // decided: lock icon
  });

  it('does not move when the decline is cancelled', async () => {
    const { component } = await render();
    const moved = component.move(applications[2], column('declined'));
    dialogResult.next(false);
    dialogResult.complete();
    expect(await moved).toBeNull();
    expect(applicationsService.updateApplicationStatus).not.toHaveBeenCalled();
  });

  it('asks before reopening a decided application', async () => {
    const { component } = await render();
    const moved = component.move(applications[3], column('tech-done'));
    expect(dialog.open.mock.lastCall![1].data.title).toBe("Reopen Ann Smith4's application?");
    dialogResult.next(true);
    dialogResult.complete();
    await moved;
    expect(applicationsService.updateApplicationStatus).toHaveBeenCalledWith(4, {
      status: S.TechnicalInterviewed,
    });
  });

  it('reverts the row and reports when the update fails', async () => {
    applicationsService.updateApplicationStatus.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { details: 'Nope' } })),
    );
    const { el, harness, component } = await render();
    expect(await component.move(applications[0], column('hr-done'))).toBeNull();
    await harness.fixture.whenStable();
    expect(stageOf(el, 'Ann Smith1')).toBe('New');
    expect(snackBar.open).toHaveBeenCalledWith('Nope', 'Close', expect.anything());
  });

  it('moves selected candidates in bulk, never to Offer or Declined', async () => {
    const { el, harness, component } = await render();
    expect(component.bulkTargets.map((c) => c.id)).toEqual([
      'hr-scheduled',
      'hr-done',
      'tech-scheduled',
      'tech-done',
    ]);
    component.toggleAll();
    await harness.fixture.whenStable();
    // The decided (offer) application can't be selected
    expect([...component.selection()]).toEqual([1, 2, 3]);
    expect(el.querySelector('.bulk-bar')?.textContent).toContain('3 selected');

    await component.moveSelected(column('hr-scheduled'));
    await harness.fixture.whenStable();
    // Application 3 is already there, so only 1 and 2 are moved
    expect(applicationsService.updateApplicationStatus).toHaveBeenCalledTimes(2);
    expect(stageOf(el, 'Ann Smith2')).toBe('HR interview scheduled');
    expect(component.selection().size).toBe(0);
    expect(snackBar.open).toHaveBeenCalledWith(
      '2 candidates moved to HR interview scheduled.',
      'Close',
      expect.anything(),
    );
  });

  it('reports partial failures of a bulk move', async () => {
    applicationsService.updateApplicationStatus.mockImplementation(
      (id: number, req: { status: S }) =>
        id === 2
          ? throwError(() => new Error('down'))
          : of({ ...applications.find((a) => a.id === id)!, status: req.status }),
    );
    const { component } = await render();
    component.toggle(applications[0]);
    component.toggle(applications[1]);
    await component.moveSelected(column('hr-done'));
    expect(snackBar.open).toHaveBeenCalledWith(
      "1 candidate moved to HR interviewed; 1 candidate couldn't be moved.",
      'Close',
      expect.anything(),
    );
  });

  it('clears the selection when switching tabs', async () => {
    const { harness, component } = await render();
    component.toggle(applications[0]);
    await harness.navigateByUrl('/companies/7/jobs/5?stage=hr');
    await harness.fixture.whenStable();
    expect(component.selection().size).toBe(0);
  });

  it('shows technical interviewers only the technical stage, read-only', async () => {
    me = employee(CompanyRole.TechnicalInterviewer);
    const { el, component } = await render();
    expect(tabLabels(el)).toEqual(['Technical 0']);
    expect(el.querySelector('mat-checkbox')).toBeNull();
    expect(el.textContent).toContain('give technical feedback');
    expect(await component.move(applications[0], column('hr-done'))).toBeNull();
  });

  it('opens the application panel from a row', async () => {
    const { el } = await render();
    rowEls(el)[0].click();
    expect(dialog.open).toHaveBeenCalledWith(
      ApplicationPanel,
      expect.objectContaining({
        panelClass: 'side-panel',
        data: expect.objectContaining({ canManage: true, me }),
      }),
    );
  });

  it('shows not found for a job of another company', async () => {
    const { el } = await render('/companies/8/jobs/5');
    expect(el.textContent).toContain('Job listing not found');
  });
});
