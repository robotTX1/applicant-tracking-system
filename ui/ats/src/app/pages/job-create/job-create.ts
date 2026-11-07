import { Component, computed, effect, inject, input, linkedSignal, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSliderModule } from '@angular/material/slider';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { TextFieldModule } from '@angular/cdk/text-field';
import { filter, firstValueFrom, map } from 'rxjs';
import {
  CompanyEmailTemplateType,
  EmailTemplatesService,
  JobListingResponse,
  JobListingSaveRequest,
  JobListingStatus,
  JobListingsService,
  JobListingUpdateRequest,
} from '../../api';
import { CompanyStateService } from '../../services/company-state.service';
import { HasPendingChanges } from '../../guards/pending-changes.guard';
import { TemplatePreviewDialog } from '../../components/template-preview-dialog/template-preview-dialog';
import { MarkdownEditor } from '../../components/markdown-editor/markdown-editor';
import { getApiErrorDetails } from '../../utils/api-error';
import { JOB_STATUS_LABELS } from '../../utils/job-listing';

function notBlankValidator(control: AbstractControl): ValidationErrors | null {
  if (!control.value) return null;
  return String(control.value).trim().length > 0 ? null : { blank: true };
}

export interface ScoreTierInfo {
  label: string;
  description: string;
  tierClass: string;
}

@Component({
  selector: 'app-job-create',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatSliderModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatDialogModule,
    TextFieldModule,
    MarkdownEditor,
  ],
  templateUrl: './job-create.html',
  styleUrl: './job-create.scss',
})
export class JobCreate implements HasPendingChanges {
  private readonly fb = inject(FormBuilder);
  private readonly jobListingsService = inject(JobListingsService);
  private readonly emailTemplatesService = inject(EmailTemplatesService);
  protected readonly companyState = inject(CompanyStateService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute, { optional: true });
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  // Router component input binding: ?companyId= when creating, :companyId when editing
  readonly companyId = input<string | undefined>(undefined);
  // Route param :jobId — present only on the edit route
  readonly jobId = input<string | undefined>(undefined);

  readonly isEditMode = computed(() => !!this.jobId());
  readonly statusLabels = JOB_STATUS_LABELS;
  readonly Status = JobListingStatus;

  /** The job being edited; undefined in create mode. */
  readonly jobResource = rxResource({
    params: () => (this.jobId() ? Number(this.jobId()) : undefined),
    stream: ({ params: id }) => this.jobListingsService.getJobListing(id),
  });

  /** The loaded job, if it belongs to the company in the URL (otherwise treated as not found). */
  readonly editedJob = computed<JobListingResponse | null>(() => {
    if (!this.jobResource.hasValue()) return null;
    const job = this.jobResource.value();
    return job &&
      String(job.companyId) === this.companyId() &&
      this.companyState.jobManagerCompanyIds().has(job.companyId)
      ? job
      : null;
  });

  readonly editLoadFailed = computed(
    () =>
      this.isEditMode() &&
      !this.jobResource.isLoading() &&
      (!!this.jobResource.error() || (this.jobResource.hasValue() && !this.editedJob())),
  );

  /** Where Back/Cancel and a successful save go. */
  readonly returnLink = computed<(string | number)[]>(() =>
    this.isEditMode() ? ['/companies', this.companyId()!, 'jobs'] : ['/companies'],
  );

  // Effective query company id: input signal with snapshot fallback for testing
  private readonly effectiveQueryCompanyId = computed(() => {
    return this.companyId() ?? this.route?.snapshot?.queryParamMap?.get('companyId') ?? undefined;
  });

  readonly isSubmitting = signal<boolean>(false);
  readonly submitAction = signal<'draft' | 'publish' | 'save' | null>(null);
  readonly isSubmitted = signal<boolean>(false);
  readonly serverError = signal<string | null>(null);

  /** Only companies where the user can post and edit jobs (owner, co-owner, HR manager). */
  readonly companies = computed(() => this.companyState.jobManagerCompanies());
  readonly hasCompanies = computed(() => this.companies().length > 0);
  /** Member of some company, but without the right to post jobs in any of them. */
  readonly memberWithoutJobRights = computed(
    () => !this.hasCompanies() && this.companyState.hasCompany(),
  );

  // Declarative initial company selection
  readonly initialCompanyId = computed<number | null>(() => {
    const job = this.editedJob();
    if (job) return job.companyId;
    const companyList = this.companies();
    if (companyList.length === 0) return null;
    const qId = this.effectiveQueryCompanyId();
    if (qId) {
      const parsed = Number(qId);
      const matched = companyList.find((c) => c.id === parsed);
      if (matched) return matched.id;
    }
    return companyList[0].id;
  });

  // Selected company managed as a linkedSignal reset by initialCompanyId
  readonly selectedCompanyId = linkedSignal<number | null>(() => this.initialCompanyId());

  // Declarative RxResource for loading email templates
  readonly templatesResource = rxResource({
    params: () => this.selectedCompanyId() ?? undefined,
    // Errors are kept (not swallowed) so the form can explain why no templates are listed
    stream: ({ params: companyId }) => this.emailTemplatesService.getEmailTemplates(companyId),
  });

  readonly availableTemplates = computed(() =>
    this.templatesResource.hasValue() ? (this.templatesResource.value().content ?? []) : [],
  );

  readonly offerTemplates = computed(() =>
    this.availableTemplates().filter((t) => t.type === CompanyEmailTemplateType.Offer),
  );

  readonly declineTemplates = computed(() =>
    this.availableTemplates().filter((t) => t.type === CompanyEmailTemplateType.Decline),
  );

  readonly isLoadingTemplates = this.templatesResource.isLoading;

  readonly templateLoadError = computed(() => {
    return this.templatesResource.error()
      ? 'Could not load email templates for the selected company.'
      : null;
  });

  // LinkedSignals for Offer and Decline default template selections
  readonly defaultOfferTemplateId = computed<number | null>(() => {
    const offers = this.offerTemplates();
    const current = this.editedJob()?.offerEmailTemplateId;
    if (current && offers.some((t) => t.id === current)) return current;
    return offers.length > 0 ? offers[0].id : null;
  });

  readonly selectedOfferTemplateId = linkedSignal<number | null>(() =>
    this.defaultOfferTemplateId(),
  );

  readonly defaultDeclineTemplateId = computed<number | null>(() => {
    const declines = this.declineTemplates();
    const current = this.editedJob()?.declineEmailTemplateId;
    if (current && declines.some((t) => t.id === current)) return current;
    return declines.length > 0 ? declines[0].id : null;
  });

  readonly selectedDeclineTemplateId = linkedSignal<number | null>(() =>
    this.defaultDeclineTemplateId(),
  );

  readonly form = this.fb.group({
    companyId: [this.selectedCompanyId(), [Validators.required]],
    title: ['', [Validators.required, Validators.maxLength(255), notBlankValidator]],
    location: ['', [Validators.required, Validators.maxLength(255), notBlankValidator]],
    description: ['', [Validators.required, Validators.maxLength(10000), notBlankValidator]],
    aiReviewEnabled: [true, [Validators.required]],
    aiReviewMinScore: [70, [Validators.required, Validators.min(0), Validators.max(100)]],
    offerEmailTemplateId: [this.selectedOfferTemplateId(), [Validators.required]],
    declineEmailTemplateId: [this.selectedDeclineTemplateId(), [Validators.required]],
  });

  readonly isAiReviewActive = toSignal(
    this.form.controls.aiReviewEnabled.valueChanges.pipe(map((v) => Boolean(v))),
    { initialValue: true },
  );

  readonly currentScore = toSignal(
    this.form.controls.aiReviewMinScore.valueChanges.pipe(
      filter((v): v is number => v !== null && v !== undefined),
      map((v) => Number(v)),
    ),
    { initialValue: 70 },
  );

  readonly scoreTier = computed<ScoreTierInfo>(() => this.getScoreTier(this.currentScore()));

  constructor() {
    // Non-blocking fallback init if companies not preloaded by router resolver
    if (this.companies().length === 0) {
      this.companyState.init().catch((err: unknown) => {
        console.error('Failed to initialize company state:', err);
      });
    }

    // Synchronize linkedSignals into FormControls with emitEvent: false to keep form pristine
    effect(() => {
      const companyId = this.selectedCompanyId();
      if (companyId !== null && this.form.controls.companyId.value !== companyId) {
        this.form.controls.companyId.setValue(companyId, { emitEvent: false });
      }
    });

    effect(() => {
      const offerId = this.selectedOfferTemplateId();
      if (this.form.controls.offerEmailTemplateId.value !== offerId) {
        this.form.controls.offerEmailTemplateId.setValue(offerId, { emitEvent: false });
      }
    });

    effect(() => {
      const declineId = this.selectedDeclineTemplateId();
      if (this.form.controls.declineEmailTemplateId.value !== declineId) {
        this.form.controls.declineEmailTemplateId.setValue(declineId, { emitEvent: false });
      }
    });

    // Synchronize form control mutations back to linkedSignals
    this.form.controls.companyId.valueChanges.pipe(takeUntilDestroyed()).subscribe((id) => {
      if (id !== null && id !== this.selectedCompanyId()) {
        this.selectedCompanyId.set(id);
      }
    });

    this.form.controls.offerEmailTemplateId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((id) => {
        if (id !== null && id !== this.selectedOfferTemplateId()) {
          this.selectedOfferTemplateId.set(id);
        }
      });

    this.form.controls.declineEmailTemplateId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((id) => {
        if (id !== null && id !== this.selectedDeclineTemplateId()) {
          this.selectedDeclineTemplateId.set(id);
        }
      });

    // Edit mode: fill the form once the job has loaded, leaving it pristine
    effect(() => {
      const job = this.editedJob();
      if (!job || this.form.dirty) return;
      this.form.patchValue({
        title: job.title,
        location: job.location,
        description: job.description,
        aiReviewEnabled: job.aiReviewEnabled ?? false,
        aiReviewMinScore: job.aiReviewMinScore ?? 70,
      });
      this.form.markAsPristine();
    });

    // AI Review toggle and score validator synchronization
    this.form.controls.aiReviewEnabled.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((enabled) => {
        const isEnabled = Boolean(enabled);
        const scoreControl = this.form.controls.aiReviewMinScore;
        if (isEnabled) {
          scoreControl.enable({ emitEvent: false });
          scoreControl.setValidators([Validators.required, Validators.min(0), Validators.max(100)]);
          if (scoreControl.value === null || scoreControl.value === undefined) {
            scoreControl.setValue(70, { emitEvent: false });
          }
        } else {
          scoreControl.disable({ emitEvent: false });
          scoreControl.clearValidators();
        }
        scoreControl.updateValueAndValidity({ emitEvent: false });
      });
  }

  hasPendingChanges(): boolean {
    return this.form.dirty && !this.isSubmitting() && !this.isSubmitted();
  }

  openTemplatePreview(templateId: number | null): void {
    if (!templateId) return;
    const template = this.availableTemplates().find((t) => t.id === templateId);
    if (!template) return;

    this.dialog.open(TemplatePreviewDialog, {
      width: '560px',
      maxWidth: '92vw',
      data: {
        type: template.type,
        subject: template.subject,
        body: template.body,
      },
    });
  }

  getScoreTier(score: number): ScoreTierInfo {
    if (score >= 80) {
      return {
        label: 'Strict Screening (80%+)',
        description: 'Only high-scoring candidate resumes pass to recruiter review.',
        tierClass: 'tier-strict',
      };
    } else if (score >= 60) {
      return {
        label: 'Balanced Screening (Recommended: 60–79%)',
        description: 'Optimal balance of candidate match quality and application volume.',
        tierClass: 'tier-balanced',
      };
    } else {
      return {
        label: 'Lenient Screening (<60%)',
        description: 'Broad candidate pass rate, minimal filtering threshold.',
        tierClass: 'tier-lenient',
      };
    }
  }

  async saveDraft(): Promise<void> {
    await this.submitJob(JobListingStatus.Draft);
  }

  async publish(): Promise<void> {
    await this.submitJob(JobListingStatus.Published);
  }

  /** Edit mode: save the changes and keep the job's current status. */
  async saveChanges(): Promise<void> {
    const job = this.editedJob();
    if (job) await this.submitJob(job.status);
  }

  private async submitJob(status: JobListingStatus): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const rawCompanyId = this.form.controls.companyId.value;
    const offerId = this.form.controls.offerEmailTemplateId.value;
    const declineId = this.form.controls.declineEmailTemplateId.value;

    if (!rawCompanyId || !offerId || !declineId) {
      this.form.markAllAsTouched();
      return;
    }

    const aiEnabled = Boolean(this.form.controls.aiReviewEnabled.value);
    const minScore = aiEnabled ? Number(this.form.controls.aiReviewMinScore.value ?? 70) : 70;

    const payload: JobListingSaveRequest = {
      companyId: Number(rawCompanyId),
      title: this.form.controls.title.value?.trim() ?? '',
      location: this.form.controls.location.value?.trim() ?? '',
      description: this.form.controls.description.value?.trim() ?? '',
      aiReviewEnabled: aiEnabled,
      aiReviewMinScore: minScore,
      offerEmailTemplateId: Number(offerId),
      declineEmailTemplateId: Number(declineId),
      status,
    };

    const editedJob = this.editedJob();
    const actionType =
      editedJob && status === editedJob.status
        ? 'save'
        : status === JobListingStatus.Published
          ? 'publish'
          : 'draft';
    this.isSubmitting.set(true);
    this.submitAction.set(actionType);
    this.serverError.set(null);

    try {
      let response: JobListingResponse;
      let actionMsg: string;
      if (editedJob) {
        const update: JobListingUpdateRequest = {
          title: payload.title,
          location: payload.location,
          description: payload.description,
          aiReviewEnabled: payload.aiReviewEnabled,
          aiReviewMinScore: payload.aiReviewMinScore,
          offerEmailTemplateId: payload.offerEmailTemplateId,
          declineEmailTemplateId: payload.declineEmailTemplateId,
          status,
        };
        response = await firstValueFrom(
          this.jobListingsService.updateJobListing(editedJob.id, update),
        );
        actionMsg = actionType === 'publish' ? 'saved and published' : 'saved';
      } else {
        response = await firstValueFrom(this.jobListingsService.saveJobListing(payload));
        actionMsg = status === JobListingStatus.Published ? 'published' : 'saved as draft';
      }
      this.isSubmitted.set(true);
      this.snackBar.open(`Job listing "${response.title}" ${actionMsg} successfully!`, 'Close', {
        duration: 4000,
        horizontalPosition: 'end',
        verticalPosition: 'bottom',
      });
      await this.router.navigate(['/companies', response.companyId, 'jobs']);
    } catch (err: unknown) {
      this.isSubmitting.set(false);
      this.submitAction.set(null);
      this.serverError.set(
        getApiErrorDetails(err) ?? 'An error occurred while saving the job listing.',
      );
    }
  }

  cancel(): void {
    this.router.navigate(this.returnLink());
  }
}
