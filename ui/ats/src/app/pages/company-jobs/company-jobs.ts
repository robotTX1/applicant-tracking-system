import {
  Component,
  computed,
  inject,
  input,
  numberAttribute,
  resource,
  signal,
} from '@angular/core';
import { ViewportScroller } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatChipSelectionChange, MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { filter, firstValueFrom } from 'rxjs';
import { JobListingResponse, JobListingsService, JobListingStatus } from '../../api';
import { ConfirmDialog, ConfirmDialogData } from '../../components/confirm-dialog/confirm-dialog';
import { RelativeTimePipe } from '../../pipes/relative-time.pipe';
import { canManageJobs, CompanyRoleService } from '../../services/company-role.service';
import { CompanyStateService } from '../../services/company-state.service';
import { getApiErrorDetails } from '../../utils/api-error';
import { JOB_STATUS_LABELS, toJobUpdateRequest } from '../../utils/job-listing';
import { and, eq } from '../../utils/rsql';

export const COMPANY_JOBS_PAGE_SIZE = 20;

export type JobStatusFilter = 'all' | 'draft' | 'published' | 'closed';

const FILTER_STATUS: Record<JobStatusFilter, JobListingStatus | null> = {
  all: null,
  draft: JobListingStatus.Draft,
  published: JobListingStatus.Published,
  closed: JobListingStatus.Closed,
};

function toFilter(value: string | undefined): JobStatusFilter {
  return value === 'draft' || value === 'published' || value === 'closed' ? value : 'all';
}

export function buildCompanyJobsQuery(companyId: number, statusFilter: JobStatusFilter): string {
  const status = FILTER_STATUS[statusFilter];
  return and(eq('companyId', companyId), status && eq('status', status))!;
}

/** The one-click status change offered for each status. */
const PRIMARY_ACTION: Record<
  JobListingStatus,
  { target: JobListingStatus; label: string; icon: string; done: string }
> = {
  [JobListingStatus.Draft]: {
    target: JobListingStatus.Published,
    label: 'Publish',
    icon: 'publish',
    done: 'Job published. Candidates can now see it and apply.',
  },
  [JobListingStatus.Published]: {
    target: JobListingStatus.Closed,
    label: 'Close',
    icon: 'lock',
    done: 'Job closed. It no longer accepts applications.',
  },
  [JobListingStatus.Closed]: {
    target: JobListingStatus.Published,
    label: 'Reopen',
    icon: 'lock_open',
    done: 'Job reopened. Candidates can apply again.',
  },
};

@Component({
  selector: 'app-company-jobs',
  imports: [
    RouterLink,
    MatButtonModule,
    MatChipsModule,
    MatDividerModule,
    MatIconModule,
    MatMenuModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    RelativeTimePipe,
  ],
  templateUrl: './company-jobs.html',
  styleUrl: './company-jobs.scss',
})
export class CompanyJobs {
  private readonly jobListingsService = inject(JobListingsService);
  private readonly companyState = inject(CompanyStateService);
  private readonly companyRoles = inject(CompanyRoleService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly viewportScroller = inject(ViewportScroller);

  // Route param + query params (?status=&page=, page 1-based) via withComponentInputBinding
  readonly companyId = input.required({ transform: numberAttribute });
  readonly status = input<JobStatusFilter, string | undefined>('all', { transform: toFilter });
  readonly page = input(1, {
    transform: (v: string | number | undefined) => Math.max(1, numberAttribute(v, 1)),
  });

  readonly pageSize = COMPANY_JOBS_PAGE_SIZE;
  readonly skeletons = [0, 1, 2];
  readonly Status = JobListingStatus;
  readonly statusLabels = JOB_STATUS_LABELS;
  readonly primaryAction = PRIMARY_ACTION;
  readonly filters: { value: JobStatusFilter; label: string }[] = [
    { value: 'all', label: 'All' },
    { value: 'published', label: 'Published' },
    { value: 'draft', label: 'Drafts' },
    { value: 'closed', label: 'Closed' },
  ];

  /** Null while the company list loads or when the user isn't a member of this company. */
  readonly company = computed(
    () => this.companyState.companies().find((c) => c.id === this.companyId()) ?? null,
  );

  readonly role = resource({
    params: () => (this.company() ? this.companyId() : undefined),
    loader: ({ params }) => this.companyRoles.getRole(params),
  });

  readonly canManage = computed(() => canManageJobs(this.role.value()));

  /** Id of the job with a status change or delete in flight. */
  readonly busyJobId = signal<number | null>(null);

  readonly jobs = rxResource({
    params: () =>
      this.company()
        ? {
            query: buildCompanyJobsQuery(this.companyId(), this.status()),
            sort: 'createdAt,desc',
            pageNumber: this.page() - 1,
            pageSize: this.pageSize,
          }
        : undefined,
    stream: ({ params }) => this.jobListingsService.getJobListings(params),
  });

  setFilter(value: JobStatusFilter | undefined): void {
    // The chip listbox emits undefined when the selected chip is clicked again: keep the filter
    if (!value || value === this.status()) return;
    this.navigate({ status: value === 'all' ? null : value, page: null });
  }

  /** Clicking the active chip would deselect it while the list stays filtered: re-select it. */
  keepActiveFilterSelected(event: MatChipSelectionChange): void {
    if (!event.selected && event.isUserInput && event.source.value === this.status()) {
      queueMicrotask(() => event.source.select());
    }
  }

  onPage(event: PageEvent): void {
    this.navigate({ page: event.pageIndex > 0 ? event.pageIndex + 1 : null });
    this.viewportScroller.scrollToPosition([0, 0]);
  }

  applicantLabel(job: JobListingResponse): string {
    const count = job.applicationCount ?? 0;
    return count === 1 ? '1 applicant' : `${count} applicants`;
  }

  canDelete(job: JobListingResponse): boolean {
    // Deleting a job also deletes its applications (DB cascade), so only empty jobs can go
    return job.applicationCount === 0;
  }

  async changeStatus(job: JobListingResponse, target: JobListingStatus): Promise<void> {
    const ok = await this.updateStatus(job, target);
    if (!ok) return;
    const message =
      target === JobListingStatus.Draft
        ? 'Job moved back to drafts. Candidates can no longer see it.'
        : PRIMARY_ACTION[job.status].done;
    this.snackBar
      .open(message, 'Undo', { duration: 6000 })
      .onAction()
      .subscribe(() => {
        this.updateStatus({ ...job, status: target }, job.status).catch(() => undefined);
      });
  }

  deleteJob(job: JobListingResponse): void {
    const data: ConfirmDialogData = {
      title: 'Delete job listing?',
      message: `"${job.title}" will be deleted permanently. This can't be undone.`,
      confirmText: 'Delete',
      cancelText: 'Keep it',
      confirmColor: 'warn',
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data, width: '440px' })
      .afterClosed()
      .pipe(filter((confirmed) => confirmed === true))
      .subscribe(async () => {
        this.busyJobId.set(job.id);
        try {
          await firstValueFrom(this.jobListingsService.deleteJobListing(job.id));
        } catch (err: unknown) {
          this.showError(err, 'Could not delete the job listing.');
          return;
        } finally {
          this.busyJobId.set(null);
        }
        this.snackBar.open('Job listing deleted.', 'Close', { duration: 4000 });
        const page = this.jobs.hasValue() ? this.jobs.value() : undefined;
        // Removing the last job of a later page: go back one page instead of showing it empty
        if (page && page.content.length === 1 && this.page() > 1) {
          this.navigate({ page: this.page() - 1 > 1 ? this.page() - 1 : null });
        } else {
          this.jobs.reload();
        }
      });
  }

  private async updateStatus(job: JobListingResponse, target: JobListingStatus): Promise<boolean> {
    this.busyJobId.set(job.id);
    try {
      const updated = await firstValueFrom(
        this.jobListingsService.updateJobListing(job.id, toJobUpdateRequest(job, target)),
      );
      // Under a status filter the job no longer belongs on this page
      if (this.status() !== 'all') {
        this.jobs.reload();
      } else {
        this.jobs.update((page) =>
          page
            ? { ...page, content: page.content.map((j) => (j.id === updated.id ? updated : j)) }
            : page,
        );
      }
      return true;
    } catch (err: unknown) {
      this.showError(err, 'Could not change the job status.');
      return false;
    } finally {
      this.busyJobId.set(null);
    }
  }

  private showError(err: unknown, fallback: string): void {
    this.snackBar.open(getApiErrorDetails(err) ?? fallback, 'Close', { duration: 5000 });
  }

  private navigate(queryParams: Record<string, string | number | null>): void {
    this.router.navigate([], { queryParams, queryParamsHandling: 'merge' });
  }
}
