import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import {
  ApplicationsService,
  JobListingResponse,
  JobListingsService,
  JobListingStatus,
  PagedJobListingResponse,
  SearchAndPageRequest,
} from '../../api';
import { buildJobSearchQuery, JobList } from './job-list';

const COMPANY_NAMES: Record<number, string> = { 10: 'Acme', 20: 'Globex' };

function job(
  id: number,
  companyId: number,
  overrides: Partial<JobListingResponse> = {},
): JobListingResponse {
  return {
    id,
    companyId,
    company: {
      id: companyId,
      name: COMPANY_NAMES[companyId] ?? `Company ${companyId}`,
      foundingDate: '2020-01-01',
      websiteUrl: '',
      description: '',
    },
    title: `Job ${id}`,
    location: 'Budapest',
    description: `**Great** role number ${id}`,
    status: JobListingStatus.Published,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    ...overrides,
  };
}

function paged(
  content: JobListingResponse[],
  totalElements = content.length,
): PagedJobListingResponse {
  return {
    content,
    page: { size: 20, number: 0, totalElements, totalPages: Math.ceil(totalElements / 20) },
  };
}

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

describe('buildJobSearchQuery', () => {
  it('always restricts to published jobs', () => {
    expect(buildJobSearchQuery('', '')).toBe("status=='PUBLISHED'");
    expect(buildJobSearchQuery('  ', ' ')).toBe("status=='PUBLISHED'");
  });

  it('matches the keyword in title or description and the location', () => {
    expect(buildJobSearchQuery(' java ', 'Budapest')).toBe(
      "status=='PUBLISHED';(title=ilike='java',description=ilike='java');location=ilike='Budapest'",
    );
  });

  it('escapes user input', () => {
    expect(buildJobSearchQuery("x';status=='DRAFT", '')).toBe(
      "status=='PUBLISHED';(title=ilike='x\\';status==\\'DRAFT',description=ilike='x\\';status==\\'DRAFT')",
    );
  });
});

describe('JobList', () => {
  let jobListingsService: { getJobListings: ReturnType<typeof vi.fn> };
  let applicationsService: { getCurrentUserApplications: ReturnType<typeof vi.fn> };

  const jobs = [job(1, 10), job(2, 20), job(3, 10)];

  beforeEach(() => {
    jobListingsService = { getJobListings: vi.fn().mockReturnValue(of(paged(jobs))) };
    applicationsService = {
      getCurrentUserApplications: vi.fn().mockReturnValue(
        of({
          content: [{ id: 99, jobListingId: 2, userId: 'u', status: 'SUBMITTED' }],
          page: { size: 1, number: 0, totalElements: 1, totalPages: 1 },
        }),
      ),
    };

    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'jobs', component: JobList }], withComponentInputBinding()),
        { provide: JobListingsService, useValue: jobListingsService },
        { provide: ApplicationsService, useValue: applicationsService },
      ],
    });
  });

  async function render(url = '/jobs') {
    const harness = await RouterTestingHarness.create(url);
    await harness.fixture.whenStable();
    return { harness, el: harness.routeNativeElement as HTMLElement };
  }

  function lastRequest(): SearchAndPageRequest {
    return jobListingsService.getJobListings.mock.lastCall![0];
  }

  it('requests the first page of published jobs, newest first', async () => {
    await render();
    expect(lastRequest()).toEqual({
      query: "status=='PUBLISHED'",
      sort: 'createdAt,desc',
      pageNumber: 0,
      pageSize: 20,
    });
  });

  it('builds the request from URL query params (page is 1-based in the URL)', async () => {
    await render('/jobs?q=java&location=Remote&page=3');
    expect(lastRequest()).toEqual(
      expect.objectContaining({
        query: buildJobSearchQuery('java', 'Remote'),
        pageNumber: 2,
      }),
    );
  });

  it('falls back to page 1 for invalid page params', async () => {
    await render('/jobs?page=abc');
    expect(lastRequest().pageNumber).toBe(0);
  });

  it('batches the application lookup for the page (company details come with the jobs)', async () => {
    await render();
    expect(applicationsService.getCurrentUserApplications).toHaveBeenCalledTimes(1);
    expect(applicationsService.getCurrentUserApplications).toHaveBeenCalledWith({
      query: "jobListingId=in=('1','2','3')",
      pageSize: 3,
    });
  });

  it('renders job cards with company name, snippet and applied badge', async () => {
    const { el } = await render();
    const cards = el.querySelectorAll('mat-card.job-card');
    expect(cards.length).toBe(3);
    expect(cards[0].textContent).toContain('Job 1');
    expect(cards[0].textContent).toContain('Acme');
    expect(cards[0].textContent).toContain('Great role number 1');
    expect(cards[0].textContent).not.toContain('**');
    expect(cards[0].querySelector('.applied-chip')).toBeNull();
    expect(cards[1].textContent).toContain('Globex');
    expect(cards[1].querySelector('.applied-chip')).not.toBeNull();
    expect(cards[0].querySelector('a')?.getAttribute('href')).toBe('/jobs/1');
    expect(el.textContent).toContain('3 jobs found');
  });

  it('still renders cards when the application lookup fails', async () => {
    applicationsService.getCurrentUserApplications.mockReturnValue(
      throwError(() => new Error('500')),
    );
    const { el } = await render();
    const cards = el.querySelectorAll('mat-card.job-card');
    expect(cards.length).toBe(3);
    expect(cards[0].textContent).toContain('Acme');
    expect(el.querySelector('.applied-chip')).toBeNull();
  });

  it('renders cards without a company name when the backend omits company details', async () => {
    jobListingsService.getJobListings.mockReturnValue(
      of(paged([{ ...job(1, 10), company: undefined as never }])),
    );
    const { el } = await render();
    const card = el.querySelector('mat-card.job-card')!;
    expect(card.textContent).toContain('Job 1');
    expect(card.querySelector('.company-name')).toBeNull();
  });

  it('skips the lookups when there are no jobs', async () => {
    jobListingsService.getJobListings.mockReturnValue(of(paged([])));
    const { el } = await render();
    expect(applicationsService.getCurrentUserApplications).not.toHaveBeenCalled();
    expect(el.textContent).toContain('No open positions yet');
  });

  it('offers to clear filters when a search has no results', async () => {
    jobListingsService.getJobListings.mockReturnValue(of(paged([])));
    const { harness, el } = await render('/jobs?q=nothing');
    expect(el.textContent).toContain('No matching jobs');

    const clearButton = [...el.querySelectorAll('button')].find((b) =>
      b.textContent?.includes('Clear filters'),
    )!;
    clearButton.click();
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/jobs');
    expect(lastRequest().query).toBe("status=='PUBLISHED'");
  });

  it('offers to go back to page 1 when the page is past the end', async () => {
    jobListingsService.getJobListings.mockReturnValue(of(paged([], 5)));
    const { el } = await render('/jobs?page=4');
    expect(el.textContent).toContain("This page doesn't exist");
  });

  it('shows an error state with retry', async () => {
    jobListingsService.getJobListings.mockReturnValue(throwError(() => new Error('boom')));
    const { harness, el } = await render();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain("Couldn't load jobs");

    jobListingsService.getJobListings.mockReturnValue(of(paged(jobs)));
    const retry = el.querySelector<HTMLButtonElement>('[role="alert"] button')!;
    retry.click();
    await harness.fixture.whenStable();
    expect(el.querySelectorAll('mat-card.job-card').length).toBe(3);
  });

  it('writes the debounced keyword to the URL and resets the page', async () => {
    const { harness, el } = await render('/jobs?page=2');
    const input = el.querySelector<HTMLInputElement>('.keyword-field input')!;
    input.value = 'angular';
    input.dispatchEvent(new Event('input'));

    await wait(350);
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/jobs?q=angular');
    expect(lastRequest()).toEqual(
      expect.objectContaining({ query: buildJobSearchQuery('angular', ''), pageNumber: 0 }),
    );
  });

  it('does not navigate per keystroke while typing', async () => {
    const { el } = await render();
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate');
    const input = el.querySelector<HTMLInputElement>('.keyword-field input')!;
    for (const value of ['a', 'an', 'ang']) {
      input.value = value;
      input.dispatchEvent(new Event('input'));
      await wait(50);
    }
    expect(navigate).not.toHaveBeenCalled();
    await wait(350);
    expect(navigate).toHaveBeenCalledTimes(1);
  });

  it('replaces history while typing but adds an entry for a new search after a pause', async () => {
    let now = 1_000_000;
    vi.spyOn(Date, 'now').mockImplementation(() => now);
    const { el } = await render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');
    const input = el.querySelector<HTMLInputElement>('.keyword-field input')!;
    const typeAndSettle = async (value: string) => {
      input.value = value;
      input.dispatchEvent(new Event('input'));
      await wait(350);
    };

    await typeAndSettle('jav'); // first search from the unfiltered list: new entry
    now += 500;
    await typeAndSettle('java'); // still typing: replace
    now += 5000;
    await typeAndSettle('angular'); // new search after a pause: new entry

    const replaceFlags = navigate.mock.calls.map(([, extras]) => extras?.replaceUrl);
    expect(replaceFlags).toEqual([false, true, false]);
  });

  it('fills the search fields from the URL', async () => {
    const { el } = await render('/jobs?q=java&location=Remote');
    expect(el.querySelector<HTMLInputElement>('.keyword-field input')!.value).toBe('java');
    expect(el.querySelector<HTMLInputElement>('.location-field input')!.value).toBe('Remote');
  });
});
