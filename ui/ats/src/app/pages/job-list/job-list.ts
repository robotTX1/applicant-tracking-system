import {
  Component,
  computed,
  effect,
  inject,
  input,
  linkedSignal,
  numberAttribute,
  untracked,
} from '@angular/core';
import { ViewportScroller } from '@angular/common';
import { debounce, form, FormField } from '@angular/forms/signals';
import { Router, RouterLink } from '@angular/router';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { catchError, map, Observable, of, switchMap } from 'rxjs';
import {
  ApplicationsService,
  JobListingResponse,
  JobListingsService,
  JobListingStatus,
  PageResponse,
} from '../../api';
import { and, eq, ilike, isIn, or } from '../../utils/rsql';
import { stripMarkdown } from '../../utils/markdown';
import { RelativeTimePipe } from '../../pipes/relative-time.pipe';
import type { JobListBackState } from '../job-detail/job-detail';

export const JOB_LIST_PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;
/** Search changes closer together than this are one "typing burst" and share a history entry. */
const SEARCH_BURST_MS = 2000;

export interface JobCardItem {
  job: JobListingResponse;
  companyName: string | null;
  snippet: string;
  applied: boolean;
}

export interface JobSearch {
  q: string;
  location: string;
}

interface JobListResult {
  items: JobCardItem[];
  page: PageResponse;
}

/** Builds the RSQL query for the public job board: published jobs matching keyword and location. */
export function buildJobSearchQuery(keyword: string, location: string): string {
  const kw = keyword.trim();
  const loc = location.trim();
  return and(
    eq('status', JobListingStatus.Published),
    kw && or(ilike('title', kw), ilike('description', kw)),
    loc && ilike('location', loc),
  )!;
}

function sameSearch(a: JobSearch, b: JobSearch): boolean {
  return a.q.trim() === b.q.trim() && a.location.trim() === b.location.trim();
}

@Component({
  selector: 'app-job-list',
  imports: [
    FormField,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    RelativeTimePipe,
  ],
  templateUrl: './job-list.html',
  styleUrl: './job-list.scss',
})
export class JobList {
  private readonly jobListingsService = inject(JobListingsService);
  private readonly applicationsService = inject(ApplicationsService);
  private readonly router = inject(Router);
  private readonly viewportScroller = inject(ViewportScroller);
  private lastSearchNavigationAt = 0;

  // Bound from the URL query params (?q=&location=&page=) via withComponentInputBinding.
  // `page` is 1-based in the URL for readability.
  readonly q = input('', { transform: (v: string | undefined) => v ?? '' });
  readonly location = input('', { transform: (v: string | undefined) => v ?? '' });
  readonly page = input(1, {
    transform: (v: string | number | undefined) => Math.max(1, numberAttribute(v, 1)),
  });

  readonly pageSize = JOB_LIST_PAGE_SIZE;
  readonly skeletons = Array.from({ length: 6 }, (_, i) => i);

  private readonly urlSearch = computed<JobSearch>(() => ({
    q: this.q(),
    location: this.location(),
  }));

  /**
   * Search form model, reset from the URL on navigation (e.g. back/forward). If the URL only
   * differs by whitespace, the typed value is kept, so a trailing space being typed isn't wiped
   * when the trimmed value comes back from the URL.
   */
  readonly searchModel = linkedSignal<JobSearch, JobSearch>({
    source: this.urlSearch,
    computation: (url, previous) =>
      previous && sameSearch(previous.value, url) ? previous.value : url,
  });

  readonly searchForm = form(this.searchModel, (path) => {
    debounce(path.q, SEARCH_DEBOUNCE_MS);
    debounce(path.location, SEARCH_DEBOUNCE_MS);
  });

  /** Passed as router state to the detail page so its back link restores this search/page. */
  readonly backState = computed<JobListBackState>(() => {
    const query: Record<string, string | number> = {};
    if (this.q().trim()) query['q'] = this.q().trim();
    if (this.location().trim()) query['location'] = this.location().trim();
    if (this.page() > 1) query['page'] = this.page();
    return { jobListQuery: query };
  });

  readonly hasFilters = computed(() => !!this.q().trim() || !!this.location().trim());

  private readonly request = computed(() => ({
    query: buildJobSearchQuery(this.q(), this.location()),
    sort: 'createdAt,desc',
    pageNumber: this.page() - 1,
    pageSize: this.pageSize,
  }));

  readonly jobs = rxResource({
    params: this.request,
    stream: ({ params }) =>
      this.jobListingsService
        .getJobListings(params)
        .pipe(
          switchMap((paged) =>
            this.loadCardDetails(paged.content).pipe(
              map((items): JobListResult => ({ items, page: paged.page })),
            ),
          ),
        ),
  });

  constructor() {
    // Push (debounced) search changes into the URL; the URL is the source of truth for loading.
    effect(() => {
      const search = this.searchModel();
      untracked(() => {
        if (sameSearch(search, this.urlSearch())) return;
        const q = search.q.trim();
        const location = search.location.trim();
        // Refinements while still typing replace the history entry; a new search after a pause
        // adds one, so Back returns to the previous search instead of every intermediate prefix.
        const now = Date.now();
        const replaceUrl = now - this.lastSearchNavigationAt < SEARCH_BURST_MS;
        this.lastSearchNavigationAt = now;
        this.navigate({ q: q || null, location: location || null, page: null }, replaceUrl);
      });
    });
  }

  clearField(field: keyof JobSearch): void {
    this.searchModel.update((s) => ({ ...s, [field]: '' }));
  }

  clearFilters(): void {
    this.searchModel.set({ q: '', location: '' });
  }

  onPage(event: PageEvent): void {
    this.navigate({ page: event.pageIndex > 0 ? event.pageIndex + 1 : null });
    this.viewportScroller.scrollToPosition([0, 0]);
  }

  goToFirstPage(): void {
    this.navigate({ page: null });
  }

  getCompanyInitial(name: string | null): string {
    return name?.trim().charAt(0).toUpperCase() || '?';
  }

  private navigate(queryParams: Record<string, string | number | null>, replaceUrl = false): void {
    this.router.navigate([], { queryParams, queryParamsHandling: 'merge', replaceUrl });
  }

  /**
   * Loads the user's applications for the jobs on this page in one batched call, for the
   * "Applied" badges. A failure only hides the badges instead of failing the list.
   */
  private loadCardDetails(jobs: JobListingResponse[]): Observable<JobCardItem[]> {
    if (jobs.length === 0) return of([]);

    const jobIds = jobs.map((j) => j.id);

    return this.applicationsService
      .getCurrentUserApplications({ query: isIn('jobListingId', jobIds), pageSize: jobIds.length })
      .pipe(
        map((res) => new Set(res.content.map((a) => a.jobListingId))),
        catchError(() => of(new Set<number>())),
        map((appliedJobIds) =>
          jobs.map((job) => ({
            job,
            companyName: job.company?.name ?? null,
            snippet: stripMarkdown(job.description),
            applied: appliedJobIds.has(job.id),
          })),
        ),
      );
  }
}
