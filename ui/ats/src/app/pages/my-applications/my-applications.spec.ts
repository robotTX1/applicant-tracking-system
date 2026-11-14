import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import {
  ApplicationsService,
  JobApplicationStatus as S,
  JobListingResponse,
  JobListingsService,
  JobListingStatus,
  PagedUserApplicationResponse,
  SearchAndPageRequest,
  UserApplicationResponse,
} from '../../api';
import { DECIDED_STATUSES, IN_PROGRESS_STATUSES } from '../../utils/candidate-status';
import { isIn } from '../../utils/rsql';
import { MyApplications } from './my-applications';

const app = (
  id: number,
  jobListingId: number,
  status: S,
  extra: Partial<UserApplicationResponse> = {},
): UserApplicationResponse => ({
  id,
  jobListingId,
  userId: 'u',
  cvDocumentId: 100 + id,
  status,
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
  ...extra,
});

const COMPANY_NAMES: Record<number, string> = { 1: 'Acme', 2: 'Globex' };

const job = (id: number, companyId: number, title: string): JobListingResponse => ({
  id,
  companyId,
  company: {
    id: companyId,
    name: COMPANY_NAMES[companyId],
    foundingDate: '',
    websiteUrl: '',
    description: '',
  },
  title,
  location: 'Budapest',
  description: '',
  status: JobListingStatus.Published,
  createdAt: '',
  updatedAt: '',
});

const paged = (content: UserApplicationResponse[], totalElements = content.length) =>
  ({
    content,
    page: { size: 20, number: 0, totalElements, totalPages: Math.ceil(totalElements / 20) },
  }) as PagedUserApplicationResponse;

describe('MyApplications', () => {
  let applicationsService: {
    getCurrentUserApplications: ReturnType<typeof vi.fn>;
    getCurrentUserApplicationCv: ReturnType<typeof vi.fn>;
    getCurrentUserApplicationCvMetadata: ReturnType<typeof vi.fn>;
    deleteCurrentUserApplication: ReturnType<typeof vi.fn>;
  };
  let jobListingsService: { getJobListings: ReturnType<typeof vi.fn> };
  let dialogResult: Subject<boolean | undefined>;
  let dialog: { open: ReturnType<typeof vi.fn> };
  let snackBar: { open: ReturnType<typeof vi.fn> };

  const applications = [
    app(1, 10, S.AiReviewed, { applicantMessage: 'Hire me' }),
    app(2, 20, S.Declined),
    app(3, 99, S.Offer), // job 99 is closed: not returned by the job search
  ];

  beforeEach(() => {
    applicationsService = {
      getCurrentUserApplications: vi.fn().mockReturnValue(of(paged(applications))),
      getCurrentUserApplicationCv: vi.fn().mockReturnValue(of(new Blob(['%PDF']))),
      getCurrentUserApplicationCvMetadata: vi.fn().mockReturnValue(
        of({
          id: 101,
          originalDocumentName: 'my-cv.pdf',
          mimeType: 'application/pdf',
          uploadedAt: '',
        }),
      ),
      deleteCurrentUserApplication: vi.fn().mockReturnValue(of(undefined)),
    };
    jobListingsService = {
      getJobListings: vi.fn().mockReturnValue(
        of({
          content: [job(10, 1, 'Java Developer'), job(20, 2, 'QA Engineer')],
          page: { size: 2, number: 0, totalElements: 2, totalPages: 1 },
        }),
      ),
    };
    dialogResult = new Subject();
    dialog = { open: vi.fn().mockReturnValue({ afterClosed: () => dialogResult }) };
    snackBar = { open: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [{ path: 'my-applications', component: MyApplications }],
          withComponentInputBinding(),
        ),
        { provide: ApplicationsService, useValue: applicationsService },
        { provide: JobListingsService, useValue: jobListingsService },
        { provide: MatDialog, useValue: dialog },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
  });

  async function render(url = '/my-applications') {
    const harness = await RouterTestingHarness.create(url);
    await harness.fixture.whenStable();
    return { harness, el: harness.routeNativeElement as HTMLElement };
  }

  const lastRequest = (): SearchAndPageRequest =>
    applicationsService.getCurrentUserApplications.mock.lastCall![0];
  const cards = (el: HTMLElement) => [...el.querySelectorAll<HTMLElement>('.application-card')];
  const expandAll = async (el: HTMLElement, harness: RouterTestingHarness) => {
    el.querySelectorAll<HTMLElement>('mat-expansion-panel-header').forEach((h) => h.click());
    await harness.fixture.whenStable();
  };
  const buttonIn = (card: HTMLElement, text: string) =>
    [...card.querySelectorAll<HTMLButtonElement>('button, a')].find((b) =>
      b.textContent?.includes(text),
    );

  it('requests the newest applications first, 20 per page', async () => {
    await render();
    expect(lastRequest()).toEqual({
      query: undefined,
      sort: 'createdAt,desc',
      pageNumber: 0,
      pageSize: 20,
    });
  });

  it.each([
    ['active', isIn('status', IN_PROGRESS_STATUSES)],
    ['decided', isIn('status', DECIDED_STATUSES)],
  ])('filters by ?status=%s', async (status, query) => {
    await render(`/my-applications?status=${status}&page=2`);
    expect(lastRequest()).toEqual(expect.objectContaining({ query, pageNumber: 1 }));
  });

  it('batches the job lookup (company details come with the jobs)', async () => {
    await render();
    expect(jobListingsService.getJobListings).toHaveBeenCalledTimes(1);
    expect(jobListingsService.getJobListings).toHaveBeenCalledWith({
      query: "id=in=('10','20','99')",
      pageSize: 3,
    });
  });

  it('renders cards with job, company and candidate-friendly status', async () => {
    const { el } = await render();
    const [first, second] = cards(el);
    expect(first.textContent).toContain('Java Developer');
    expect(first.textContent).toContain('Acme');
    expect(first.querySelector('.status-chip')?.textContent).toContain('Under review');
    expect(first.querySelector('.cdk-visually-hidden')?.textContent).toContain(
      'Stage 2 of 5: Under review',
    );
    expect(first.querySelectorAll('.stage-progress li.done').length).toBe(2);
    expect(second.querySelector('.status-chip')?.textContent).toContain('Not selected');
    expect(second.classList).toContain('tone-declined');
  });

  it('shows applications to closed jobs without job details', async () => {
    const { el, harness } = await render();
    const closed = cards(el)[2];
    expect(closed.querySelector('.job-title')?.textContent).toContain('no longer listed');
    await expandAll(el, harness);
    expect(closed.querySelector('.unlisted-note')).not.toBeNull();
    expect(buttonIn(closed, 'View job posting')).toBeUndefined();
    expect(buttonIn(closed, 'Download my CV')).toBeDefined();
  });

  it('shows the message, job link and actions when expanded; no withdraw when declined', async () => {
    const { el, harness } = await render();
    await expandAll(el, harness);
    const [first, declined] = cards(el);
    expect(first.querySelector('.applicant-message')?.textContent).toContain('Hire me');
    expect(buttonIn(first, 'View job posting')?.getAttribute('href')).toBe('/jobs/10');
    expect(buttonIn(first, 'Withdraw')).toBeDefined();
    expect(declined.querySelector('.applicant-message.empty')).not.toBeNull();
    expect(buttonIn(declined, 'Withdraw')).toBeUndefined();
  });

  it('still renders when the job lookup fails', async () => {
    jobListingsService.getJobListings.mockReturnValue(throwError(() => new Error('500')));
    const { el } = await render();
    expect(cards(el).length).toBe(3);
    expect(cards(el)[0].querySelector('.job-title')?.textContent).toContain('no longer listed');
  });

  it('shows an empty state with a link to jobs', async () => {
    applicationsService.getCurrentUserApplications.mockReturnValue(of(paged([])));
    const { el } = await render();
    expect(el.textContent).toContain('No applications yet');
    expect(el.querySelector('a[href="/jobs"]')).not.toBeNull();
    expect(jobListingsService.getJobListings).not.toHaveBeenCalled();
  });

  it('offers "Show all" when a filter has no results', async () => {
    applicationsService.getCurrentUserApplications.mockReturnValue(of(paged([])));
    const { el, harness } = await render('/my-applications?status=decided');
    expect(el.textContent).toContain('no decided applications');
    buttonIn(el, 'Show all')!.click();
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/my-applications');
  });

  it('shows a retryable error state', async () => {
    applicationsService.getCurrentUserApplications.mockReturnValue(
      throwError(() => new Error('x')),
    );
    const { el, harness } = await render();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain("Couldn't load");
    applicationsService.getCurrentUserApplications.mockReturnValue(of(paged(applications)));
    el.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    await harness.fixture.whenStable();
    expect(cards(el).length).toBe(3);
  });

  it('downloads the CV with its original file name', async () => {
    const createObjectURL = vi.fn(() => 'blob:cv');
    const revokeObjectURL = vi.fn();
    Object.assign(URL, { createObjectURL, revokeObjectURL });
    let downloaded: { name: string; href: string } | undefined;
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      downloaded = { name: this.download, href: this.getAttribute('href')! };
    });

    const { el, harness } = await render();
    await expandAll(el, harness);
    buttonIn(cards(el)[0], 'Download my CV')!.click();
    await harness.fixture.whenStable();
    await new Promise((r) => setTimeout(r));

    expect(applicationsService.getCurrentUserApplicationCv).toHaveBeenCalledWith(1);
    expect(downloaded).toEqual({ name: 'my-cv.pdf', href: 'blob:cv' });
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:cv');
    click.mockRestore();
  });

  it('reports a failed download', async () => {
    applicationsService.getCurrentUserApplicationCv.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404, error: { details: 'CV not found' } })),
    );
    const { el, harness } = await render();
    await expandAll(el, harness);
    buttonIn(cards(el)[0], 'Download my CV')!.click();
    await harness.fixture.whenStable();
    expect(snackBar.open).toHaveBeenCalledWith('CV not found', 'Close', { duration: 5000 });
  });

  describe('withdraw', () => {
    it('asks for confirmation and does nothing when cancelled', async () => {
      const { el, harness } = await render();
      await expandAll(el, harness);
      buttonIn(cards(el)[0], 'Withdraw')!.click();
      expect(dialog.open.mock.lastCall![1].data.message).toContain("can't be undone");
      dialogResult.next(false);
      expect(applicationsService.deleteCurrentUserApplication).not.toHaveBeenCalled();
    });

    it('deletes after confirmation and reloads the list', async () => {
      const { el, harness } = await render();
      await expandAll(el, harness);
      buttonIn(cards(el)[0], 'Withdraw')!.click();
      dialogResult.next(true);
      await harness.fixture.whenStable();
      expect(applicationsService.deleteCurrentUserApplication).toHaveBeenCalledWith(1);
      expect(applicationsService.getCurrentUserApplications).toHaveBeenCalledTimes(2);
      expect(snackBar.open).toHaveBeenCalledWith('Application withdrawn.', 'Close', {
        duration: 4000,
      });
    });

    it('goes back a page when withdrawing the last card of a later page', async () => {
      applicationsService.getCurrentUserApplications.mockReturnValue(
        of(paged([app(1, 10, S.Applied)], 21)),
      );
      const { el, harness } = await render('/my-applications?page=2');
      await expandAll(el, harness);
      buttonIn(cards(el)[0], 'Withdraw')!.click();
      dialogResult.next(true);
      await harness.fixture.whenStable();
      expect(TestBed.inject(Router).url).toBe('/my-applications');
    });

    it('reports a failed withdraw', async () => {
      applicationsService.deleteCurrentUserApplication.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 500 })),
      );
      const { el, harness } = await render();
      await expandAll(el, harness);
      buttonIn(cards(el)[0], 'Withdraw')!.click();
      dialogResult.next(true);
      await harness.fixture.whenStable();
      expect(snackBar.open).toHaveBeenCalledWith('Could not withdraw the application.', 'Close', {
        duration: 5000,
      });
      expect(applicationsService.getCurrentUserApplications).toHaveBeenCalledTimes(1);
    });
  });

  it('keeps the active filter selected when its chip is clicked again', async () => {
    const { el, harness } = await render('/my-applications?status=decided');
    const decided = [...el.querySelectorAll<HTMLElement>('mat-chip-option')].find((c) =>
      c.textContent?.includes('Decided'),
    )!;
    decided.querySelector<HTMLElement>('[role="option"], button')!.click();
    await new Promise((r) => setTimeout(r));
    await harness.fixture.whenStable();
    expect(decided.querySelector('[aria-selected]')?.getAttribute('aria-selected')).toBe('true');
    expect(TestBed.inject(Router).url).toBe('/my-applications?status=decided');
  });
});
