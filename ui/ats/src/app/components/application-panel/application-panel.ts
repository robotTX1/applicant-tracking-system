import { Component, computed, DOCUMENT, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MarkdownComponent } from 'ngx-markdown';
import { filter, firstValueFrom, forkJoin, map } from 'rxjs';
import {
  ApplicationFeedbackType,
  ApplicationsService,
  EmployeeResponse,
  FeedbackResponse,
  FeedbacksService,
  JobApplicationResponse,
  JobApplicationStatus,
  JobListingResponse,
} from '../../api';
import { provideAppMarkdown } from '../../markdown.config';
import { RelativeTimePipe } from '../../pipes/relative-time.pipe';
import { canGiveHrFeedback, canGiveTechnicalFeedback } from '../../services/company-role.service';
import { getApiErrorDetails } from '../../utils/api-error';
import { saveBlob } from '../../utils/download';
import {
  applicantName,
  columnOf,
  isDecided,
  moveTargets,
  PipelineColumn,
  scoreTone,
} from '../../utils/pipeline';
import { ConfirmDialog, ConfirmDialogData } from '../confirm-dialog/confirm-dialog';

export const FEEDBACK_MAX_LENGTH = 2000;

export interface ApplicationPanelData {
  application: JobApplicationResponse;
  job: JobListingResponse;
  /** The signed-in user's employee record in the job's company. */
  me: EmployeeResponse | null;
  /** Company employees by id, for feedback author names. */
  employees: Map<number, EmployeeResponse>;
  canManage: boolean;
  /**
   * Moves the application (with confirmations); resolves to the updated application or null.
   * `onConfirmed` runs once the user has confirmed, right before the request is sent.
   */
  move: (
    application: JobApplicationResponse,
    column: PipelineColumn,
    onConfirmed?: () => void,
  ) => Promise<JobApplicationResponse | null>;
}

/** The stage in which each feedback type can be given, edited and deleted. */
const FEEDBACK_STAGE: Record<ApplicationFeedbackType, JobApplicationStatus> = {
  [ApplicationFeedbackType.Hr]: JobApplicationStatus.HrInterviewed,
  [ApplicationFeedbackType.Technical]: JobApplicationStatus.TechnicalInterviewed,
};

/** Used mid-sentence ("Add HR interview feedback"), so only proper acronyms are capitalised. */
export const FEEDBACK_ADD_LABELS: Record<ApplicationFeedbackType, string> = {
  [ApplicationFeedbackType.Hr]: 'Add HR interview feedback',
  [ApplicationFeedbackType.Technical]: 'Add technical interview feedback',
};

export const FEEDBACK_TYPE_LABELS: Record<ApplicationFeedbackType, string> = {
  [ApplicationFeedbackType.Hr]: 'HR interview',
  [ApplicationFeedbackType.Technical]: 'Technical interview',
};

function notBlank(control: AbstractControl): ValidationErrors | null {
  return String(control.value ?? '').trim() ? null : { blank: true };
}

@Component({
  selector: 'app-application-panel',
  imports: [
    NgTemplateOutlet,
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatMenuModule,
    MatProgressSpinnerModule,
    MarkdownComponent,
    RelativeTimePipe,
  ],
  providers: [provideAppMarkdown()],
  templateUrl: './application-panel.html',
  styleUrl: './application-panel.scss',
})
export class ApplicationPanel {
  readonly data = inject<ApplicationPanelData>(MAT_DIALOG_DATA);
  private readonly applicationsService = inject(ApplicationsService);
  private readonly feedbacksService = inject(FeedbacksService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly document = inject(DOCUMENT);
  private readonly fb = inject(FormBuilder);

  readonly maxLength = FEEDBACK_MAX_LENGTH;
  readonly typeLabels = FEEDBACK_TYPE_LABELS;
  readonly addLabels = FEEDBACK_ADD_LABELS;
  readonly Status = JobApplicationStatus;
  readonly scoreTone = scoreTone;
  readonly name = applicantName(this.data.application);

  readonly application = signal(this.data.application);
  readonly column = computed(() => columnOf(this.application().status));
  readonly decided = computed(() => isDecided(this.application().status));
  readonly targets = computed(() => moveTargets(this.application().status));
  readonly moving = signal(false);
  readonly downloading = signal(false);

  readonly feedbacks = rxResource({
    params: () => this.application().id,
    stream: ({ params }) =>
      this.feedbacksService
        .getApplicationFeedbacks(params, { sort: 'createdAt,asc', pageSize: 100 })
        .pipe(map((res) => res.content)),
  });

  /** The feedback type the user can add now, if any (one per person per type, in its stage). */
  readonly addableType = computed<ApplicationFeedbackType | null>(() => {
    const me = this.data.me;
    if (!me || !this.feedbacks.hasValue()) return null;
    const status = this.application().status;
    const type =
      status === JobApplicationStatus.HrInterviewed && canGiveHrFeedback(me.role)
        ? ApplicationFeedbackType.Hr
        : status === JobApplicationStatus.TechnicalInterviewed && canGiveTechnicalFeedback(me.role)
          ? ApplicationFeedbackType.Technical
          : null;
    if (!type) return null;
    const alreadyGiven = this.feedbacks
      .value()
      .some((f) => f.type === type && f.employeeId === me.id);
    return alreadyGiven ? null : type;
  });

  /** Explains why there is no feedback form, when that isn't obvious. */
  readonly feedbackHint = computed<string | null>(() => {
    if (this.addableType() || this.editingId() !== null) return null;
    const status = this.application().status;
    if (
      status !== JobApplicationStatus.HrInterviewed &&
      status !== JobApplicationStatus.TechnicalInterviewed
    ) {
      return 'Feedback can be given while the candidate is in HR interviewed or Tech interviewed.';
    }
    return null;
  });

  /** Id of the feedback being edited, 'new' while adding, or null. */
  readonly editingId = signal<number | 'new' | null>(null);
  readonly saving = signal(false);
  readonly deletingId = signal<number | null>(null);

  readonly feedbackForm = this.fb.group({
    score: this.fb.control<number | null>(null, [
      Validators.required,
      Validators.min(0),
      Validators.max(100),
      Validators.pattern(/^\d+$/),
    ]),
    feedback: this.fb.nonNullable.control('', [
      Validators.required,
      Validators.maxLength(FEEDBACK_MAX_LENGTH),
      notBlank,
    ]),
  });

  authorName(feedback: FeedbackResponse): string {
    if (feedback.employeeId === this.data.me?.id) return 'You';
    const author = this.data.employees.get(feedback.employeeId);
    return author ? `${author.firstName} ${author.lastName}` : 'A former team member';
  }

  canModify(feedback: FeedbackResponse): boolean {
    return (
      feedback.employeeId === this.data.me?.id &&
      this.application().status === FEEDBACK_STAGE[feedback.type]
    );
  }

  startAdding(): void {
    this.feedbackForm.reset({ score: null, feedback: '' });
    this.editingId.set('new');
  }

  startEditing(feedback: FeedbackResponse): void {
    this.feedbackForm.reset({ score: feedback.score, feedback: feedback.feedback });
    this.editingId.set(feedback.id);
  }

  cancelEditing(): void {
    this.editingId.set(null);
  }

  async saveFeedback(): Promise<void> {
    const editing = this.editingId();
    if (editing === null || this.feedbackForm.invalid) {
      this.feedbackForm.markAllAsTouched();
      return;
    }
    const body = {
      score: Number(this.feedbackForm.controls.score.value),
      feedback: this.feedbackForm.controls.feedback.value.trim(),
    };
    const appId = this.application().id;
    this.saving.set(true);
    try {
      const saved = await firstValueFrom(
        editing === 'new'
          ? this.feedbacksService.saveApplicationFeedback(appId, body)
          : this.feedbacksService.updateApplicationFeedback(appId, editing, body),
      );
      this.feedbacks.update((list) =>
        editing === 'new'
          ? [...(list ?? []), saved]
          : list?.map((f) => (f.id === saved.id ? saved : f)),
      );
      this.editingId.set(null);
    } catch (err: unknown) {
      this.showError(err, 'Could not save the feedback.');
    } finally {
      this.saving.set(false);
    }
  }

  deleteFeedback(feedback: FeedbackResponse): void {
    const data: ConfirmDialogData = {
      title: 'Delete your feedback?',
      message: "Your feedback and score will be removed. This can't be undone.",
      confirmText: 'Delete',
      cancelText: 'Keep it',
      confirmColor: 'warn',
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data, width: '420px' })
      .afterClosed()
      .pipe(filter((confirmed) => confirmed === true))
      .subscribe(async () => {
        this.deletingId.set(feedback.id);
        try {
          await firstValueFrom(
            this.feedbacksService.deleteApplicationFeedback(this.application().id, feedback.id),
          );
          this.feedbacks.update((list) => list?.filter((f) => f.id !== feedback.id));
        } catch (err: unknown) {
          this.showError(err, 'Could not delete the feedback.');
        } finally {
          this.deletingId.set(null);
        }
      });
  }

  async moveTo(column: PipelineColumn): Promise<void> {
    try {
      // Spin only once any confirmation (offer / decline / reopen) has been accepted
      const updated = await this.data.move(this.application(), column, () => this.moving.set(true));
      if (updated) {
        this.application.set(updated);
        this.editingId.set(null);
      }
    } finally {
      this.moving.set(false);
    }
  }

  async downloadCv(): Promise<void> {
    const { jobListingId, id } = this.application();
    this.downloading.set(true);
    try {
      const [metadata, blob] = await firstValueFrom(
        forkJoin([
          this.applicationsService.getJobListingApplicationCvMetadata(jobListingId, id),
          this.applicationsService.getJobListingApplicationCv(jobListingId, id),
        ]),
      );
      saveBlob(this.document, blob, metadata.originalDocumentName || `cv-${id}.pdf`);
    } catch (err: unknown) {
      this.showError(err, 'Could not download the CV.');
    } finally {
      this.downloading.set(false);
    }
  }

  private showError(err: unknown, fallback: string): void {
    this.snackBar.open(getApiErrorDetails(err) ?? fallback, 'Close', { duration: 5000 });
  }
}
