import {
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  numberAttribute,
} from '@angular/core';
import { DatePipe, Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MarkdownComponent } from 'ngx-markdown';
import { catchError, map, Observable, of, switchMap } from 'rxjs';
import {
  ApplicationsService,
  CompanyResponse,
  JobListingResponse,
  JobListingsService,
  JobListingStatus,
  UserApplicationResponse,
} from '../../api';
import { eq } from '../../utils/rsql';
import { provideAppMarkdown } from '../../markdown.config';
import { RelativeTimePipe } from '../../pipes/relative-time.pipe';
import { ExternalUrlPipe } from '../../pipes/external-url.pipe';
import { CompanyStateService } from '../../services/company-state.service';
import {
  ApplyDialog,
  ApplyDialogData,
  ApplyDialogResult,
} from '../../components/apply-dialog/apply-dialog';

export interface JobDetailData {
  job: JobListingResponse;
  company: CompanyResponse | null;
  application: UserApplicationResponse | null;
}

/** Router state set by the job list so "Back to jobs" restores the search and page. */
export interface JobListBackState {
  jobListQuery?: Record<string, string | number>;
}

function readBackQuery(state: unknown): Record<string, string | number> | null {
  const query = (state as JobListBackState | null)?.jobListQuery;
  if (!query || typeof query !== 'object') return null;
  const entries = Object.entries(query).filter(
    ([key, value]) =>
      ['q', 'location', 'page'].includes(key) &&
      (typeof value === 'string' || typeof value === 'number'),
  );
  return entries.length ? Object.fromEntries(entries) : null;
}

@Component({
  selector: 'app-job-detail',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MarkdownComponent,
    RelativeTimePipe,
    ExternalUrlPipe,
  ],
  // Provided here (not app-wide) so marked stays in this lazy chunk instead of the initial bundle
  providers: [provideAppMarkdown()],
  templateUrl: './job-detail.html',
  styleUrl: './job-detail.scss',
})
export class JobDetail {
  private readonly jobListingsService = inject(JobListingsService);
  private readonly applicationsService = inject(ApplicationsService);
  private readonly title = inject(Title);
  private readonly companyState = inject(CompanyStateService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  /** Route param `:id`, bound via withComponentInputBinding. */
  readonly id = input.required({ transform: numberAttribute });

  readonly backQuery = readBackQuery(inject(Location).getState());

  private readonly validId = computed(() => {
    const id = this.id();
    return Number.isInteger(id) && id > 0 ? id : undefined;
  });

  readonly detail = rxResource({
    params: this.validId,
    stream: ({ params: id }) =>
      this.jobListingsService.getJobListing(id).pipe(switchMap((job) => this.loadRelated(job))),
  });

  readonly notFound = computed(() => {
    if (this.validId() === undefined) return true;
    const error = this.detail.error();
    // ResourceErrors wrap the original error in `cause`
    const cause = (error as { cause?: unknown } | undefined)?.cause ?? error;
    return cause instanceof HttpErrorResponse && (cause.status === 404 || cause.status === 400);
  });

  /** Loaded data, or undefined while loading / on error (`value()` throws in the error state). */
  readonly data = computed(() => (this.detail.hasValue() ? this.detail.value() : undefined));
  readonly job = computed(() => this.data()?.job);
  readonly isPublished = computed(() => this.job()?.status === JobListingStatus.Published);
  readonly statusLabel = computed(() => {
    switch (this.job()?.status) {
      case JobListingStatus.Draft:
        return 'Draft · not visible to candidates';
      case JobListingStatus.Closed:
        return 'Closed · no longer accepting applications';
      default:
        return null;
    }
  });

  /** The backend rejects applications from employees of the job's company. */
  readonly isOwnCompany = computed(() => {
    const companyId = this.job()?.companyId;
    return companyId !== undefined && this.companyState.companies().some((c) => c.id === companyId);
  });

  readonly canApply = computed(
    () => this.isPublished() && !this.data()?.application && !this.isOwnCompany(),
  );

  constructor() {
    this.companyState.init().catch(() => {
      // Without the user's companies we just can't hide Apply on their own jobs; the backend
      // still rejects those applications with a clear message.
    });

    const originalTitle = this.title.getTitle();
    this.destroyRef.onDestroy(() => this.title.setTitle(originalTitle));

    effect(() => {
      const data = this.data();
      if (!data) return;
      const company = data.company ? ` at ${data.company.name}` : '';
      this.title.setTitle(`${data.job.title}${company} · Jobs`);
    });
  }

  openApplyDialog(): void {
    const data = this.data();
    if (!data) return;
    const dialogData: ApplyDialogData = { job: data.job, companyName: data.company?.name ?? null };
    const ref = this.dialog.open<ApplyDialog, ApplyDialogData, ApplyDialogResult>(ApplyDialog, {
      data: dialogData,
      width: '560px',
      maxWidth: 'calc(100vw - 32px)',
      autoFocus: 'first-tabbable',
    });

    ref.afterClosed().subscribe((result) => {
      if (!result) return;
      if (result.status === 'already-applied') {
        this.snackBar.open('You have already applied to this job.', 'Close', { duration: 5000 });
        this.detail.reload();
        return;
      }
      this.detail.value.update((current) =>
        current ? { ...current, application: result.application } : current,
      );
      this.snackBar.open(
        result.profileCvSaveFailed
          ? 'Application submitted, but your CV could not be saved to your profile.'
          : 'Application submitted. Good luck!',
        'Close',
        { duration: 5000 },
      );
    });
  }

  getCompanyInitial(name: string | undefined): string {
    return name?.trim().charAt(0).toUpperCase() || '?';
  }

  /**
   * The user's existing application is loaded alongside the job (company details come with the
   * job). A failure only hides the "applied" state instead of failing the whole page.
   */
  private loadRelated(job: JobListingResponse): Observable<JobDetailData> {
    return this.applicationsService
      .getCurrentUserApplications({ query: eq('jobListingId', job.id), pageSize: 1 })
      .pipe(
        map((res) => res.content[0] ?? null),
        catchError(() => of(null)),
        map((application) => ({ job, company: job.company ?? null, application })),
      );
  }
}
