import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { Title } from '@angular/platform-browser';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import {
  ApplicationsService,
  CompanyResponse,
  JobApplicationStatus,
  JobListingResponse,
  JobListingsService,
  JobListingStatus,
} from '../../api';
import { JobDetail } from './job-detail';
import { CompanyStateService } from '../../services/company-state.service';
import { ApplyDialog, ApplyDialogResult } from '../../components/apply-dialog/apply-dialog';

const job: JobListingResponse = {
  id: 7,
  companyId: 3,
  company: {
    id: 3,
    name: 'Acme',
    foundingDate: '2020-01-01',
    websiteUrl: 'https://acme.io',
    description: 'We build things.',
  },
  title: 'Senior Java Developer',
  location: 'Budapest',
  description: '# Responsibilities\n\n- Build **APIs**\n\n[Our blog](https://acme.io/blog)',
  status: JobListingStatus.Published,
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
};

describe('JobDetail', () => {
  let jobListingsService: { getJobListing: ReturnType<typeof vi.fn> };
  let applicationsService: { getCurrentUserApplications: ReturnType<typeof vi.fn> };
  let myCompanies: ReturnType<typeof signal<CompanyResponse[]>>;
  let dialogClosed: Subject<ApplyDialogResult | undefined>;
  let dialog: { open: ReturnType<typeof vi.fn> };
  let snackBar: { open: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    jobListingsService = { getJobListing: vi.fn().mockReturnValue(of(job)) };
    applicationsService = {
      getCurrentUserApplications: vi
        .fn()
        .mockReturnValue(
          of({ content: [], page: { size: 1, number: 0, totalElements: 0, totalPages: 0 } }),
        ),
    };

    myCompanies = signal<CompanyResponse[]>([]);
    dialogClosed = new Subject();
    dialog = { open: vi.fn().mockReturnValue({ afterClosed: () => dialogClosed }) };
    snackBar = { open: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        {
          provide: CompanyStateService,
          useValue: { companies: myCompanies, init: vi.fn().mockResolvedValue(undefined) },
        },
        { provide: MatDialog, useValue: dialog },
        { provide: MatSnackBar, useValue: snackBar },
        provideRouter(
          [
            { path: 'jobs', children: [] },
            { path: 'jobs/:id', component: JobDetail },
          ],
          withComponentInputBinding(),
        ),
        { provide: JobListingsService, useValue: jobListingsService },
        { provide: ApplicationsService, useValue: applicationsService },
      ],
    });
  });

  async function render(url = '/jobs/7') {
    const harness = await RouterTestingHarness.create(url);
    await harness.fixture.whenStable();
    // ngx-markdown renders asynchronously
    await new Promise((resolve) => setTimeout(resolve));
    await harness.fixture.whenStable();
    return { harness, el: harness.routeNativeElement as HTMLElement };
  }

  it('loads the job by route id with the application', async () => {
    await render();
    expect(jobListingsService.getJobListing).toHaveBeenCalledWith(7);
    expect(applicationsService.getCurrentUserApplications).toHaveBeenCalledWith({
      query: "jobListingId=='7'",
      pageSize: 1,
    });
  });

  it('renders the header, company card and Markdown description', async () => {
    const { el } = await render();
    expect(el.querySelector('h1')?.textContent).toContain('Senior Java Developer');
    expect(el.querySelector('.job-meta')?.textContent).toContain('Acme');
    expect(el.querySelector('.job-meta')?.textContent).toContain('Budapest');
    expect(el.querySelector('.company-card')?.textContent).toContain('We build things.');

    const description = el.querySelector('.markdown-body')!;
    expect(description.querySelector('h3')?.textContent).toBe('Responsibilities');
    expect(description.querySelector('li strong')?.textContent).toBe('APIs');
    const link = description.querySelector('a')!;
    expect(link.getAttribute('target')).toBe('_blank');
    expect(link.getAttribute('rel')).toContain('noopener');
  });

  it('links the company website with a scheme added when missing', async () => {
    jobListingsService.getJobListing.mockReturnValue(
      of({ ...job, company: { ...job.company, websiteUrl: 'www.acme.io' } }),
    );
    const { el } = await render();
    expect(el.querySelector('.website-link')?.getAttribute('href')).toBe('https://www.acme.io/');
  });

  it('keeps a single h1 on the page', async () => {
    const { el } = await render();
    expect(el.querySelectorAll('h1').length).toBe(1);
  });

  it('sets the document title and restores it on leave', async () => {
    const title = TestBed.inject(Title);
    title.setTitle('Ats');
    const { harness } = await render();
    expect(title.getTitle()).toBe('Senior Java Developer at Acme · Jobs');

    await harness.navigateByUrl('/jobs');
    expect(title.getTitle()).toBe('Ats');
  });

  it('shows the applied banner when the user already applied', async () => {
    applicationsService.getCurrentUserApplications.mockReturnValue(
      of({
        content: [
          {
            id: 1,
            jobListingId: 7,
            userId: 'u',
            cvDocumentId: 1,
            status: JobApplicationStatus.Applied,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          },
        ],
        page: { size: 1, number: 0, totalElements: 1, totalPages: 1 },
      }),
    );
    const { el } = await render();
    expect(el.querySelector('.applied-banner')?.textContent).toContain('You applied');
    expect(el.querySelector('a[href="/my-applications"]')).not.toBeNull();
  });

  it('shows a status chip for closed jobs', async () => {
    jobListingsService.getJobListing.mockReturnValue(
      of({ ...job, status: JobListingStatus.Closed }),
    );
    const { el } = await render();
    expect(el.querySelector('.status-chip')?.textContent).toContain('Closed');
  });

  it('still renders when the application lookup fails', async () => {
    applicationsService.getCurrentUserApplications.mockReturnValue(
      throwError(() => new Error('500')),
    );
    const { el } = await render();
    expect(el.querySelector('h1')?.textContent).toContain('Senior Java Developer');
    expect(el.querySelector('.company-card')).not.toBeNull();
    expect(el.querySelector('.applied-banner')).toBeNull();
  });

  it('renders without the company card when the backend omits company details', async () => {
    jobListingsService.getJobListing.mockReturnValue(of({ ...job, company: undefined as never }));
    const { el } = await render();
    expect(el.querySelector('h1')?.textContent).toContain('Senior Java Developer');
    expect(el.querySelector('.company-card')).toBeNull();
  });

  it('shows "not found" on 404', async () => {
    jobListingsService.getJobListing.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404 })),
    );
    const { el } = await render();
    expect(el.querySelector('h1')?.textContent).toContain('Job not found');
  });

  it('shows "not found" for invalid ids without calling the API', async () => {
    const { el } = await render('/jobs/abc');
    expect(jobListingsService.getJobListing).not.toHaveBeenCalled();
    expect(el.querySelector('h1')?.textContent).toContain('Job not found');
  });

  it('shows a retryable error for other failures', async () => {
    jobListingsService.getJobListing.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );
    const { harness, el } = await render();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain("Couldn't load this job");

    jobListingsService.getJobListing.mockReturnValue(of(job));
    el.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    await harness.fixture.whenStable();
    expect(el.querySelector('h1')?.textContent).toContain('Senior Java Developer');
  });

  it('links back to the list with the search it came from', async () => {
    const harness = await RouterTestingHarness.create();
    await TestBed.inject(Router).navigateByUrl('/jobs/7', {
      state: { jobListQuery: { q: 'java', page: 2, evil: 'x' } },
    });
    await harness.fixture.whenStable();
    const el = harness.routeNativeElement as HTMLElement;
    expect(el.querySelector('.back-link')?.getAttribute('href')).toBe('/jobs?q=java&page=2');
  });

  it('links back to the plain list without state', async () => {
    const { el } = await render();
    expect(el.querySelector('.back-link')?.getAttribute('href')).toBe('/jobs');
  });

  describe('applying', () => {
    const applyButton = (el: HTMLElement) => el.querySelector<HTMLButtonElement>('.apply-button');
    const application = {
      id: 1,
      jobListingId: 7,
      userId: 'u',
      cvDocumentId: 1,
      status: JobApplicationStatus.Applied,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    it('offers Apply on published jobs the user has not applied to', async () => {
      const { el } = await render();
      expect(applyButton(el)?.textContent).toContain('Apply now');
    });

    it('hides Apply on closed jobs', async () => {
      jobListingsService.getJobListing.mockReturnValue(
        of({ ...job, status: JobListingStatus.Closed }),
      );
      const { el } = await render();
      expect(applyButton(el)).toBeNull();
    });

    it("explains instead of offering Apply on the user's own company's jobs", async () => {
      myCompanies.set([{ id: 3, name: 'Acme', foundingDate: '', websiteUrl: '', description: '' }]);
      const { el } = await render();
      expect(applyButton(el)).toBeNull();
      expect(el.querySelector('.own-company-note')?.textContent).toContain(
        'You work at this company',
      );
    });

    it('opens the dialog with the job and company name', async () => {
      const { el } = await render();
      applyButton(el)!.click();
      expect(dialog.open).toHaveBeenCalledWith(
        ApplyDialog,
        expect.objectContaining({ data: { job, companyName: 'Acme' } }),
      );
    });

    it('shows the applied banner without reloading after a successful application', async () => {
      const { harness, el } = await render();
      applyButton(el)!.click();
      dialogClosed.next({ status: 'applied', application, profileCvSaveFailed: false });
      await harness.fixture.whenStable();

      expect(el.querySelector('.applied-banner')?.textContent).toContain('You applied');
      expect(applyButton(el)).toBeNull();
      expect(jobListingsService.getJobListing).toHaveBeenCalledTimes(1);
      expect(snackBar.open).toHaveBeenCalledWith('Application submitted. Good luck!', 'Close', {
        duration: 5000,
      });
    });

    it('mentions when the CV could not be saved to the profile', async () => {
      const { harness, el } = await render();
      applyButton(el)!.click();
      dialogClosed.next({ status: 'applied', application, profileCvSaveFailed: true });
      await harness.fixture.whenStable();
      expect(snackBar.open.mock.lastCall![0]).toContain('could not be saved to your profile');
    });

    it('reloads when the backend says the user already applied', async () => {
      const { harness, el } = await render();
      applicationsService.getCurrentUserApplications.mockReturnValue(
        of({
          content: [application],
          page: { size: 1, number: 0, totalElements: 1, totalPages: 1 },
        }),
      );
      applyButton(el)!.click();
      dialogClosed.next({ status: 'already-applied' });
      await harness.fixture.whenStable();

      expect(jobListingsService.getJobListing).toHaveBeenCalledTimes(2);
      expect(el.querySelector('.applied-banner')).not.toBeNull();
    });

    it('does nothing when the dialog is cancelled', async () => {
      const { harness, el } = await render();
      applyButton(el)!.click();
      dialogClosed.next(undefined);
      await harness.fixture.whenStable();
      expect(applyButton(el)).not.toBeNull();
      expect(snackBar.open).not.toHaveBeenCalled();
    });
  });
});
