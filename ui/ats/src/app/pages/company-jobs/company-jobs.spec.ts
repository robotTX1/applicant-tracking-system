import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import {
  CompanyResponse,
  CompanyRole,
  JobListingResponse,
  JobListingsService,
  JobListingStatus,
  PagedJobListingResponse,
  SearchAndPageRequest,
} from '../../api';
import { CompanyRoleService } from '../../services/company-role.service';
import { CompanyStateService } from '../../services/company-state.service';
import { CompanyJobs } from './company-jobs';

const company: CompanyResponse = {
  id: 7,
  name: 'Acme',
  foundingDate: '2020-01-01',
  websiteUrl: '',
  description: '',
};

const job = (
  id: number,
  status: JobListingStatus,
  applicationCount: number,
  title = `Job ${id}`,
): JobListingResponse => ({
  id,
  companyId: 7,
  company,
  title,
  location: 'Budapest',
  description: 'Desc',
  aiReviewEnabled: true,
  aiReviewMinScore: 60,
  offerEmailTemplateId: 1,
  declineEmailTemplateId: 2,
  applicationCount,
  status,
  createdAt: '2026-11-01T10:00:00Z',
  updatedAt: '2026-11-01T10:00:00Z',
});

const paged = (content: JobListingResponse[], totalElements = content.length) =>
  ({
    content,
    page: { size: 20, number: 0, totalElements, totalPages: Math.ceil(totalElements / 20) },
  }) as PagedJobListingResponse;

describe('CompanyJobs', () => {
  let jobListingsService: {
    getJobListings: ReturnType<typeof vi.fn>;
    updateJobListing: ReturnType<typeof vi.fn>;
    deleteJobListing: ReturnType<typeof vi.fn>;
  };
  let role: CompanyRole | null;
  let dialogResult: Subject<boolean | undefined>;
  let dialog: { open: ReturnType<typeof vi.fn> };
  let snackAction: Subject<void>;
  let snackBar: { open: ReturnType<typeof vi.fn> };

  const jobs = [
    job(1, JobListingStatus.Draft, 0, 'Java Developer'),
    job(2, JobListingStatus.Published, 3, 'QA Engineer'),
    job(3, JobListingStatus.Closed, 1, 'Designer'),
  ];

  beforeEach(() => {
    role = CompanyRole.HrManager;
    jobListingsService = {
      getJobListings: vi.fn().mockReturnValue(of(paged(jobs))),
      updateJobListing: vi
        .fn()
        .mockImplementation((id: number, req: { status: JobListingStatus }) =>
          of({ ...jobs.find((j) => j.id === id)!, status: req.status }),
        ),
      deleteJobListing: vi.fn().mockReturnValue(of(undefined)),
    };
    dialogResult = new Subject();
    dialog = { open: vi.fn().mockReturnValue({ afterClosed: () => dialogResult }) };
    snackAction = new Subject();
    snackBar = { open: vi.fn().mockReturnValue({ onAction: () => snackAction }) };

    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [{ path: 'companies/:companyId/jobs', component: CompanyJobs }],
          withComponentInputBinding(),
        ),
        { provide: JobListingsService, useValue: jobListingsService },
        { provide: CompanyStateService, useValue: { companies: signal([company]) } },
        { provide: CompanyRoleService, useValue: { getRole: () => Promise.resolve(role) } },
        { provide: MatDialog, useValue: dialog },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
  });

  async function render(url = '/companies/7/jobs') {
    const harness = await RouterTestingHarness.create(url);
    await harness.fixture.whenStable();
    return {
      harness,
      el: harness.routeNativeElement as HTMLElement,
      component: harness.routeDebugElement!.componentInstance as CompanyJobs,
    };
  }

  const lastRequest = (): SearchAndPageRequest =>
    jobListingsService.getJobListings.mock.lastCall![0];
  const cards = (el: HTMLElement) => [...el.querySelectorAll<HTMLElement>('li.job-card')];
  const buttonIn = (root: HTMLElement, text: string) =>
    [...root.querySelectorAll<HTMLButtonElement>('button, a')].find((b) =>
      b.textContent?.includes(text),
    );

  it('requests every status of this company, newest first', async () => {
    await render();
    expect(lastRequest()).toEqual({
      query: "companyId=='7'",
      sort: 'createdAt,desc',
      pageNumber: 0,
      pageSize: 20,
    });
  });

  it('filters by ?status= and ?page=', async () => {
    await render('/companies/7/jobs?status=closed&page=3');
    expect(lastRequest()).toEqual(
      expect.objectContaining({ query: "companyId=='7';status=='CLOSED'", pageNumber: 2 }),
    );
  });

  it('renders status, applicant count and the matching primary action', async () => {
    const { el } = await render();
    const [draft, published, closed] = cards(el);
    expect(el.querySelector('.breadcrumb')?.textContent).toContain('Acme');
    expect(draft.querySelector('.status-chip')?.textContent).toContain('Draft');
    expect(draft.querySelector('.applicant-count')?.textContent).toContain('0 applicants');
    expect(published.querySelector('.applicant-count')?.textContent).toContain('3 applicants');
    expect(closed.querySelector('.applicant-count')?.textContent).toContain('1 applicant');
    expect(buttonIn(draft, 'Publish')).toBeDefined();
    expect(buttonIn(published, 'Close')).toBeDefined();
    expect(buttonIn(closed, 'Reopen')).toBeDefined();
    expect(buttonIn(el, 'Post job')?.getAttribute('href')).toBe('/jobs/new?companyId=7');
  });

  it('hides management actions and filters from technical interviewers', async () => {
    role = CompanyRole.TechnicalInterviewer;
    const { el } = await render();
    expect(cards(el).length).toBe(3);
    expect(el.querySelector('.job-actions')).toBeNull();
    expect(el.querySelector('mat-chip-listbox')).toBeNull();
    expect(buttonIn(el, 'Post job')).toBeUndefined();
  });

  it('shows not found for a company the user is not a member of', async () => {
    const { el } = await render('/companies/99/jobs');
    expect(el.textContent).toContain('Company not found');
    expect(jobListingsService.getJobListings).not.toHaveBeenCalled();
  });

  it('publishes a draft with the full listing and updates the card in place', async () => {
    const { el, harness } = await render();
    buttonIn(cards(el)[0], 'Publish')!.click();
    await harness.fixture.whenStable();

    expect(jobListingsService.updateJobListing).toHaveBeenCalledWith(1, {
      title: 'Java Developer',
      location: 'Budapest',
      description: 'Desc',
      aiReviewEnabled: true,
      aiReviewMinScore: 60,
      offerEmailTemplateId: 1,
      declineEmailTemplateId: 2,
      status: JobListingStatus.Published,
    });
    expect(cards(el)[0].querySelector('.status-chip')?.textContent).toContain('Published');
    expect(jobListingsService.getJobListings).toHaveBeenCalledTimes(1);
    expect(snackBar.open).toHaveBeenCalledWith(
      expect.stringContaining('Job published'),
      'Undo',
      expect.anything(),
    );
  });

  it('undoes a status change from the snackbar', async () => {
    const { el, harness } = await render();
    buttonIn(cards(el)[1], 'Close')!.click();
    await harness.fixture.whenStable();
    snackAction.next();
    await harness.fixture.whenStable();

    expect(jobListingsService.updateJobListing).toHaveBeenLastCalledWith(
      2,
      expect.objectContaining({ status: JobListingStatus.Published }),
    );
    expect(cards(el)[1].querySelector('.status-chip')?.textContent).toContain('Published');
  });

  it('reloads under a status filter, since the job leaves the filtered list', async () => {
    const { el, harness } = await render('/companies/7/jobs?status=published');
    buttonIn(cards(el)[1], 'Close')!.click();
    await harness.fixture.whenStable();
    expect(jobListingsService.getJobListings).toHaveBeenCalledTimes(2);
  });

  it('reports a failed status change', async () => {
    jobListingsService.updateJobListing.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 400, error: { details: 'Template missing' } }),
      ),
    );
    const { el, harness } = await render();
    buttonIn(cards(el)[0], 'Publish')!.click();
    await harness.fixture.whenStable();
    expect(snackBar.open).toHaveBeenCalledWith('Template missing', 'Close', expect.anything());
    expect(cards(el)[0].querySelector('.status-chip')?.textContent).toContain('Draft');
  });

  it('only allows deleting jobs without applicants', async () => {
    const { component } = await render();
    expect(component.canDelete(jobs[0])).toBe(true);
    expect(component.canDelete(jobs[1])).toBe(false);
  });

  it('deletes after confirmation and reloads', async () => {
    const { component, harness } = await render();
    component.deleteJob(jobs[0]);
    expect(jobListingsService.deleteJobListing).not.toHaveBeenCalled();
    dialogResult.next(true);
    await harness.fixture.whenStable();
    expect(jobListingsService.deleteJobListing).toHaveBeenCalledWith(1);
    expect(jobListingsService.getJobListings).toHaveBeenCalledTimes(2);
  });

  it('does nothing when the delete is cancelled', async () => {
    const { component, harness } = await render();
    component.deleteJob(jobs[0]);
    dialogResult.next(false);
    await harness.fixture.whenStable();
    expect(jobListingsService.deleteJobListing).not.toHaveBeenCalled();
  });

  it('shows an empty state with a call to action', async () => {
    jobListingsService.getJobListings.mockReturnValue(of(paged([])));
    const { el } = await render();
    expect(el.textContent).toContain('No job listings yet');
  });

  it('shows an error state with retry', async () => {
    jobListingsService.getJobListings.mockReturnValue(throwError(() => new Error('boom')));
    const { el } = await render();
    expect(el.textContent).toContain("Couldn't load the job listings");
  });
});
