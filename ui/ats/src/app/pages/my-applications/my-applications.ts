import {
  Component,
  computed,
  DOCUMENT,
  inject,
  input,
  numberAttribute,
  signal,
} from '@angular/core';
import { ViewportScroller } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatChipSelectionChange, MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, filter, firstValueFrom, forkJoin, map, Observable, of, switchMap } from 'rxjs';
import {
  ApplicationsService,
  JobListingResponse,
  JobListingsService,
  PageResponse,
  UserApplicationResponse,
} from '../../api';
import { ConfirmDialog, ConfirmDialogData } from '../../components/confirm-dialog/confirm-dialog';
import { RelativeTimePipe } from '../../pipes/relative-time.pipe';
import { getApiErrorDetails } from '../../utils/api-error';
import {
  CANDIDATE_STAGES,
  CandidateStatus,
  DECIDED_STATUSES,
  IN_PROGRESS_STATUSES,
  toCandidateStatus,
} from '../../utils/candidate-status';
import { saveBlob } from '../../utils/download';
import { isIn } from '../../utils/rsql';

export const MY_APPLICATIONS_PAGE_SIZE = 20;

export type ApplicationFilter = 'all' | 'active' | 'decided';

export interface ApplicationCardItem {
  application: UserApplicationResponse;
  /** Null when the job is no longer published (candidates can't see closed/draft jobs). */
  job: JobListingResponse | null;
  companyName: string | null;
  status: CandidateStatus;
}

interface ApplicationsResult {
  items: ApplicationCardItem[];
  page: PageResponse;
}

const FILTER_QUERY: Record<ApplicationFilter, string | undefined> = {
  all: undefined,
  active: isIn('status', IN_PROGRESS_STATUSES),
  decided: isIn('status', DECIDED_STATUSES),
};

function toFilter(value: string | undefined): ApplicationFilter {
  return value === 'active' || value === 'decided' ? value : 'all';
}

@Component({
  selector: 'app-my-applications',
  imports: [
    RouterLink,
    MatButtonModule,
    MatChipsModule,
    MatExpansionModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    RelativeTimePipe,
  ],
  templateUrl: './my-applications.html',
  styleUrl: './my-applications.scss',
})
export class MyApplications {
  private readonly applicationsService = inject(ApplicationsService);
  private readonly jobListingsService = inject(JobListingsService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly document = inject(DOCUMENT);
  private readonly viewportScroller = inject(ViewportScroller);

  // Bound from ?status=&page= (page is 1-based in the URL)
  readonly status = input<ApplicationFilter, string | undefined>('all', { transform: toFilter });
  readonly page = input(1, {
    transform: (v: string | number | undefined) => Math.max(1, numberAttribute(v, 1)),
  });

  readonly pageSize = MY_APPLICATIONS_PAGE_SIZE;
  readonly stages = CANDIDATE_STAGES;
  readonly skeletons = [0, 1, 2];
  readonly filters: { value: ApplicationFilter; label: string }[] = [
    { value: 'all', label: 'All' },
    { value: 'active', label: 'In progress' },
    { value: 'decided', label: 'Decided' },
  ];

  readonly withdrawingId = signal<number | null>(null);
  readonly downloadingId = signal<number | null>(null);

  private readonly request = computed(() => ({
    query: FILTER_QUERY[this.status()],
    sort: 'createdAt,desc',
    pageNumber: this.page() - 1,
    pageSize: this.pageSize,
  }));

  readonly applications = rxResource({
    params: this.request,
    stream: ({ params }) =>
      this.applicationsService
        .getCurrentUserApplications(params)
        .pipe(
          switchMap((paged) =>
            this.loadCardDetails(paged.content).pipe(
              map((items): ApplicationsResult => ({ items, page: paged.page })),
            ),
          ),
        ),
  });

  setFilter(value: ApplicationFilter | undefined): void {
    // The chip listbox emits undefined when the selected chip is clicked again: keep the filter
    if (!value || value === this.status()) return;
    this.navigate({ status: value === 'all' ? null : value, page: null });
  }

  /**
   * Clicking the active filter chip would deselect it, leaving nothing selected while the list
   * stays filtered. Re-select it (it stays a normal, selectable option for screen readers).
   */
  keepActiveFilterSelected(event: MatChipSelectionChange): void {
    if (!event.selected && event.isUserInput && event.source.value === this.status()) {
      queueMicrotask(() => event.source.select());
    }
  }

  onPage(event: PageEvent): void {
    this.navigate({ page: event.pageIndex > 0 ? event.pageIndex + 1 : null });
    this.viewportScroller.scrollToPosition([0, 0]);
  }

  jobTitle(item: ApplicationCardItem): string {
    return item.job?.title ?? 'A job that is no longer listed';
  }

  progressLabel(status: CandidateStatus): string {
    return status.step === null
      ? `Status: ${status.label}`
      : `Stage ${status.step + 1} of ${this.stages.length}: ${status.label}`;
  }

  async downloadCv(item: ApplicationCardItem): Promise<void> {
    const id = item.application.id;
    this.downloadingId.set(id);
    try {
      const [metadata, blob] = await firstValueFrom(
        forkJoin([
          this.applicationsService.getCurrentUserApplicationCvMetadata(id),
          this.applicationsService.getCurrentUserApplicationCv(id),
        ]),
      );
      saveBlob(this.document, blob, metadata.originalDocumentName || `cv-${id}.pdf`);
    } catch (err: unknown) {
      this.snackBar.open(getApiErrorDetails(err) ?? 'Could not download the CV.', 'Close', {
        duration: 5000,
      });
    } finally {
      this.downloadingId.set(null);
    }
  }

  withdraw(item: ApplicationCardItem): void {
    const title = this.jobTitle(item);
    const data: ConfirmDialogData = {
      title: 'Withdraw application?',
      message: `Your application for "${title}" and the CV you sent with it will be deleted. This can't be undone.`,
      confirmText: 'Withdraw',
      cancelText: 'Keep application',
      confirmColor: 'warn',
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data, width: '440px' })
      .afterClosed()
      .pipe(
        filter((confirmed) => confirmed === true),
        switchMap(() => {
          this.withdrawingId.set(item.application.id);
          return this.applicationsService.deleteCurrentUserApplication(item.application.id).pipe(
            map(() => true),
            catchError((err: unknown) => {
              this.snackBar.open(
                getApiErrorDetails(err) ?? 'Could not withdraw the application.',
                'Close',
                { duration: 5000 },
              );
              return of(false);
            }),
          );
        }),
      )
      .subscribe((withdrawn) => {
        this.withdrawingId.set(null);
        if (!withdrawn) return;
        this.snackBar.open('Application withdrawn.', 'Close', { duration: 4000 });
        const result = this.applications.hasValue() ? this.applications.value() : undefined;
        // Removing the last card of a later page: go back one page instead of showing it empty
        if (result && result.items.length === 1 && this.page() > 1) {
          this.navigate({ page: this.page() - 1 > 1 ? this.page() - 1 : null });
        } else {
          this.applications.reload();
        }
      });
  }

  private navigate(queryParams: Record<string, string | number | null>): void {
    this.router.navigate([], { queryParams, queryParamsHandling: 'merge' });
  }

  /**
   * Job titles and company names in one batched call. Candidates only see published jobs, so
   * applications to closed jobs get `job: null`; a lookup failure degrades the same way.
   */
  private loadCardDetails(
    applications: UserApplicationResponse[],
  ): Observable<ApplicationCardItem[]> {
    if (applications.length === 0) return of([]);
    const jobIds = [...new Set(applications.map((a) => a.jobListingId))];

    return this.jobListingsService
      .getJobListings({ query: isIn('id', jobIds), pageSize: jobIds.length })
      .pipe(
        map((res) => res.content),
        catchError(() => of([] as JobListingResponse[])),
        map((jobs) => {
          const jobsById = new Map(jobs.map((j) => [j.id, j]));
          return applications.map((application) => {
            const job = jobsById.get(application.jobListingId) ?? null;
            return {
              application,
              job,
              companyName: job?.company?.name ?? null,
              status: toCandidateStatus(application.status),
            };
          });
        }),
      );
  }
}
