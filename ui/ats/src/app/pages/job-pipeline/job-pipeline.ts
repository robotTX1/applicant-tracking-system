import {
  Component,
  computed,
  effect,
  inject,
  input,
  numberAttribute,
  resource,
  signal,
  untracked,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipSelectionChange, MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { EMPTY, expand, firstValueFrom, map, Observable, reduce } from 'rxjs';
import {
  ApplicationsService,
  EmployeeResponse,
  EmployeesService,
  JobApplicationResponse,
  JobApplicationStatus,
  JobListingsService,
} from '../../api';
import {
  ApplicationPanel,
  ApplicationPanelData,
} from '../../components/application-panel/application-panel';
import { ConfirmDialog, ConfirmDialogData } from '../../components/confirm-dialog/confirm-dialog';
import { RelativeTimePipe } from '../../pipes/relative-time.pipe';
import { canManageApplications, CompanyRoleService } from '../../services/company-role.service';
import { CompanyStateService } from '../../services/company-state.service';
import { getApiErrorDetails } from '../../utils/api-error';
import { JOB_STATUS_LABELS } from '../../utils/job-listing';
import {
  applicantName,
  ApplicationSortKey,
  columnOf,
  compareApplications,
  inTab,
  isDecided,
  moveTargets,
  PIPELINE_COLUMNS,
  PIPELINE_TABS,
  PipelineColumn,
  PipelineTab,
  PipelineTabId,
  scoreTone,
  SortDirection,
} from '../../utils/pipeline';

/** The API's page size limit; the page loads every application of the job. */
const FETCH_PAGE_SIZE = 100;

const SORT_KEYS: readonly ApplicationSortKey[] = ['name', 'aiScore', 'stage', 'createdAt'];

function toTab(value: string | undefined): PipelineTabId {
  return PIPELINE_TABS.some((t) => t.id === value) ? (value as PipelineTabId) : 'all';
}

function toSortKey(value: string | undefined): ApplicationSortKey {
  return SORT_KEYS.includes(value as ApplicationSortKey)
    ? (value as ApplicationSortKey)
    : 'aiScore';
}

function toDirection(value: string | undefined): SortDirection {
  return value === 'asc' ? 'asc' : 'desc';
}

@Component({
  selector: 'app-job-pipeline',
  imports: [
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatChipsModule,
    MatIconModule,
    MatMenuModule,
    MatProgressSpinnerModule,
    MatSortModule,
    MatTableModule,
    MatTooltipModule,
    RelativeTimePipe,
  ],
  templateUrl: './job-pipeline.html',
  styleUrl: './job-pipeline.scss',
})
export class JobPipeline {
  private readonly jobListingsService = inject(JobListingsService);
  private readonly applicationsService = inject(ApplicationsService);
  private readonly employeesService = inject(EmployeesService);
  private readonly companyState = inject(CompanyStateService);
  private readonly companyRoles = inject(CompanyRoleService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);

  readonly companyId = input.required({ transform: numberAttribute });
  readonly jobId = input.required({ transform: numberAttribute });
  // Query params: ?stage=&sort=&dir=
  readonly stage = input<PipelineTabId, string | undefined>('all', { transform: toTab });
  readonly sort = input<ApplicationSortKey, string | undefined>('aiScore', {
    transform: toSortKey,
  });
  readonly dir = input<SortDirection, string | undefined>('desc', { transform: toDirection });

  readonly statusLabels = JOB_STATUS_LABELS;
  readonly Status = JobApplicationStatus;
  readonly applicantName = applicantName;
  readonly isDecided = isDecided;
  readonly moveTargets = moveTargets;
  readonly scoreTone = scoreTone;

  readonly company = computed(
    () => this.companyState.companies().find((c) => c.id === this.companyId()) ?? null,
  );

  readonly me = resource({
    params: () => (this.company() ? this.companyId() : undefined),
    loader: ({ params }) => this.companyRoles.getEmployee(params),
  });

  readonly role = computed(() => this.me.value()?.role ?? null);
  readonly canManage = computed(() => canManageApplications(this.role()));

  readonly job = rxResource({
    params: () => (this.company() ? this.jobId() : undefined),
    stream: ({ params }) => this.jobListingsService.getJobListing(params),
  });

  /** Null when the job belongs to another company than the one in the URL. */
  readonly jobInCompany = computed(() => {
    const job = this.job.hasValue() ? this.job.value() : undefined;
    return job && job.companyId === this.companyId() ? job : null;
  });

  readonly applications = rxResource({
    params: () => (this.jobInCompany() ? this.jobId() : undefined),
    stream: ({ params }) => this.loadAllApplications(params),
  });

  /** Names for feedback authors; a failure only hides the names. */
  readonly employees = rxResource({
    params: () => (this.company() ? this.companyId() : undefined),
    stream: ({ params }) =>
      this.employeesService
        .getEmployees(params, { pageSize: FETCH_PAGE_SIZE })
        .pipe(map((res) => new Map(res.content.map((e) => [e.id, e])))),
    defaultValue: new Map<number, EmployeeResponse>(),
  });

  /** Technical interviewers only see (and the backend only returns) the technical stages. */
  readonly technicalOnly = computed(() => this.me.hasValue() && !this.canManage());

  readonly tabs = computed<PipelineTab[]>(() =>
    this.technicalOnly() ? PIPELINE_TABS.filter((t) => t.id === 'technical') : [...PIPELINE_TABS],
  );

  readonly activeTab = computed<PipelineTab>(
    () => this.tabs().find((t) => t.id === this.stage()) ?? this.tabs()[0],
  );

  private readonly allApplications = computed(() =>
    this.applications.hasValue() ? this.applications.value() : [],
  );

  readonly tabCounts = computed(() => {
    const apps = this.allApplications();
    return new Map(this.tabs().map((t) => [t.id, apps.filter((a) => inTab(a, t)).length]));
  });

  readonly rows = computed(() => {
    const tab = this.activeTab();
    return this.allApplications()
      .filter((a) => inTab(a, tab))
      .sort(compareApplications(this.sort(), this.dir()));
  });

  readonly displayedColumns = computed(() =>
    this.canManage()
      ? ['select', 'name', 'aiScore', 'stage', 'createdAt', 'actions']
      : ['name', 'aiScore', 'stage', 'createdAt'],
  );

  /** Selected application ids (managers only). Decided applications can't be selected. */
  readonly selection = signal<ReadonlySet<number>>(new Set());
  readonly bulkMoving = signal(false);

  readonly selectableRows = computed(() => this.rows().filter((a) => !isDecided(a.status)));
  readonly selectedRows = computed(() => this.rows().filter((a) => this.selection().has(a.id)));
  readonly allSelected = computed(
    () =>
      this.selectableRows().length > 0 &&
      this.selectableRows().every((a) => this.selection().has(a.id)),
  );

  /** Bulk moves never email candidates: Offer and Declined are decided one by one. */
  readonly bulkTargets = PIPELINE_COLUMNS.filter((c) => c.target !== null && !c.sendsEmail);

  readonly notFound = computed(
    () =>
      !this.company() ||
      (!!this.job.error() && !this.job.isLoading()) ||
      (this.job.hasValue() && !this.jobInCompany()),
  );

  constructor() {
    // A new tab shows different rows: start with nothing selected
    effect(() => {
      this.activeTab();
      untracked(() => this.selection.set(new Set()));
    });
  }

  selectTab(id: PipelineTabId | undefined): void {
    // The chip listbox emits undefined when the selected chip is clicked again: keep the tab
    if (!id || id === this.activeTab().id) return;
    this.navigate({ stage: id === 'all' ? null : id });
  }

  /** Clicking the active chip would deselect it while the list stays filtered: re-select it. */
  keepActiveTabSelected(event: MatChipSelectionChange): void {
    if (!event.selected && event.isUserInput && event.source.value === this.activeTab().id) {
      queueMicrotask(() => event.source.select());
    }
  }

  onSort(sort: Sort): void {
    const key = toSortKey(sort.active);
    // MatSort cycles asc → desc → none; "none" falls back to the default order
    if (!sort.direction) {
      this.navigate({ sort: null, dir: null });
    } else {
      this.navigate({ sort: key, dir: sort.direction });
    }
  }

  toggle(application: JobApplicationResponse): void {
    this.selection.update((set) => {
      const next = new Set(set);
      if (next.has(application.id)) next.delete(application.id);
      else next.add(application.id);
      return next;
    });
  }

  toggleAll(): void {
    this.selection.set(
      this.allSelected() ? new Set() : new Set(this.selectableRows().map((a) => a.id)),
    );
  }

  clearSelection(): void {
    this.selection.set(new Set());
  }

  /** Moves every selected application (already there = skipped) and reports the result. */
  async moveSelected(column: PipelineColumn): Promise<void> {
    const target = column.target;
    if (!target || column.sendsEmail || !this.canManage()) return;
    const toMove = this.selectedRows().filter((a) => a.status !== target && !isDecided(a.status));
    if (toMove.length === 0) {
      this.clearSelection();
      return;
    }
    this.bulkMoving.set(true);
    const results = await Promise.allSettled(
      toMove.map((a) =>
        firstValueFrom(this.applicationsService.updateApplicationStatus(a.id, { status: target })),
      ),
    );
    this.bulkMoving.set(false);
    let failed = 0;
    results.forEach((result) => {
      if (result.status === 'fulfilled') this.replace(result.value);
      else failed++;
    });
    this.clearSelection();
    const moved = toMove.length - failed;
    const who = (n: number) => (n === 1 ? '1 candidate' : `${n} candidates`);
    this.snackBar.open(
      failed === 0
        ? `${who(moved)} moved to ${column.title}.`
        : `${who(moved)} moved to ${column.title}; ${who(failed)} couldn't be moved.`,
      'Close',
      { duration: 5000 },
    );
  }

  stageOf(application: JobApplicationResponse): PipelineColumn {
    return columnOf(application.status);
  }

  openPanel(application: JobApplicationResponse): void {
    const job = this.jobInCompany();
    if (!job) return;
    const data: ApplicationPanelData = {
      application,
      job,
      me: this.me.value() ?? null,
      employees: this.employees.value(),
      canManage: this.canManage(),
      move: (app, column, onConfirmed) => this.move(app, column, onConfirmed),
    };
    this.dialog.open(ApplicationPanel, {
      data,
      panelClass: 'side-panel',
      position: { right: '0', top: '0' },
      height: '100dvh',
      width: '560px',
      maxWidth: '100vw',
      autoFocus: 'dialog',
      ariaLabel: `Application of ${applicantName(application)}`,
    });
  }

  /**
   * Moves an application to a stage after any needed confirmation. The row updates right away
   * and reverts if the request fails. Resolves to the updated application, or null.
   */
  async move(
    application: JobApplicationResponse,
    column: PipelineColumn,
    onConfirmed?: () => void,
  ): Promise<JobApplicationResponse | null> {
    const target = column.target;
    if (!target || target === application.status || !this.canManage()) return null;
    if (!(await this.confirmMove(application, column))) return null;
    onConfirmed?.();

    this.replace({ ...application, status: target });
    try {
      const updated = await firstValueFrom(
        this.applicationsService.updateApplicationStatus(application.id, { status: target }),
      );
      this.replace(updated);
      if (column.sendsEmail) {
        this.snackBar.open(
          `${applicantName(application)} moved to ${column.title}. The email is on its way.`,
          'Close',
          { duration: 5000 },
        );
      }
      return updated;
    } catch (err: unknown) {
      this.replace(application);
      this.snackBar.open(
        getApiErrorDetails(err) ?? `Could not move ${applicantName(application)}.`,
        'Close',
        { duration: 5000 },
      );
      return null;
    }
  }

  moveLabel(application: JobApplicationResponse, column: PipelineColumn): string {
    return `Move ${applicantName(application)} to ${column.title}`;
  }

  private async confirmMove(
    application: JobApplicationResponse,
    column: PipelineColumn,
  ): Promise<boolean> {
    const name = applicantName(application);
    const reopening = isDecided(application.status);
    let data: ConfirmDialogData | null = null;

    if (column.sendsEmail) {
      const kind = column.id === 'offer' ? 'an offer' : 'a rejection';
      data = {
        title: column.id === 'offer' ? `Make an offer to ${name}?` : `Decline ${name}?`,
        message:
          `${name} will be emailed ${kind} right away, using this job's ` +
          `${column.id === 'offer' ? 'offer' : 'decline'} template. Emails can't be unsent.` +
          (reopening ? ' They already received a decision email for this application.' : ''),
        confirmText: column.id === 'offer' ? 'Send offer' : 'Decline and send email',
        cancelText: 'Cancel',
        confirmColor: column.id === 'offer' ? 'primary' : 'warn',
      };
    } else if (reopening) {
      data = {
        title: `Reopen ${name}'s application?`,
        message:
          `${name} has already been emailed the ` +
          `${application.status === JobApplicationStatus.Offer ? 'offer' : 'rejection'}. Moving them back to ` +
          `${column.title} won't send another email, so let them know yourself if needed.`,
        confirmText: 'Reopen',
        cancelText: 'Cancel',
        confirmColor: 'warn',
      };
    }
    if (!data) return true;

    const confirmed = await firstValueFrom(
      this.dialog
        .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data, width: '460px' })
        .afterClosed(),
    );
    return confirmed === true;
  }

  private navigate(queryParams: Record<string, string | null>): void {
    this.router.navigate([], { queryParams, queryParamsHandling: 'merge', replaceUrl: true });
  }

  private replace(application: JobApplicationResponse): void {
    this.applications.update((list) =>
      list?.map((a) => (a.id === application.id ? application : a)),
    );
  }

  private loadAllApplications(jobId: number): Observable<JobApplicationResponse[]> {
    const page = (pageNumber: number) =>
      this.applicationsService.getJobListingApplications(jobId, {
        sort: 'createdAt,asc',
        pageNumber,
        pageSize: FETCH_PAGE_SIZE,
      });
    return page(0).pipe(
      expand((res) =>
        res.page.number + 1 < res.page.totalPages ? page(res.page.number + 1) : EMPTY,
      ),
      reduce((all, res) => all.concat(res.content), [] as JobApplicationResponse[]),
    );
  }
}
