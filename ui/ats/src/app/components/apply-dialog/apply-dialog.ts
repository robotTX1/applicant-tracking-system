import { Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { rxResource } from '@angular/core/rxjs-interop';
import { form, FormField, maxLength } from '@angular/forms/signals';
import { TextFieldModule } from '@angular/cdk/text-field';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { catchError, firstValueFrom, of, throwError } from 'rxjs';
import {
  ApplicationsService,
  DocumentResponse,
  JobListingResponse,
  UserApplicationResponse,
  UsersService,
} from '../../api';

import { getApiErrorDetails } from '../../utils/api-error';
import { CV_ACCEPT, formatFileSize, validateCvFile } from '../../utils/cv-file';

export const MAX_MESSAGE_LENGTH = 2000;

export interface ApplyDialogData {
  job: JobListingResponse;
  companyName: string | null;
}

export type ApplyDialogResult =
  | { status: 'applied'; application: UserApplicationResponse; profileCvSaveFailed: boolean }
  /** The backend says the user already applied (e.g. from another tab): the page should reload. */
  | { status: 'already-applied' };

type CvSource = 'profile' | 'upload';

@Component({
  selector: 'app-apply-dialog',
  imports: [
    DatePipe,
    FormField,
    TextFieldModule,
    MatDialogModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatRadioModule,
  ],
  templateUrl: './apply-dialog.html',
  styleUrl: './apply-dialog.scss',
})
export class ApplyDialog {
  private readonly dialogRef = inject<MatDialogRef<ApplyDialog, ApplyDialogResult>>(MatDialogRef);
  private readonly applicationsService = inject(ApplicationsService);
  private readonly usersService = inject(UsersService);
  readonly data = inject<ApplyDialogData>(MAT_DIALOG_DATA);

  readonly maxMessageLength = MAX_MESSAGE_LENGTH;
  readonly cvAccept = CV_ACCEPT;
  readonly formatFileSize = formatFileSize;

  /** The user's default CV, or null if they haven't uploaded one (404). */
  readonly profileCv = rxResource({
    stream: () =>
      this.usersService
        .getCurrentUserCvMetadata()
        .pipe(
          catchError((err: unknown) =>
            err instanceof HttpErrorResponse && err.status === 404
              ? of(null as DocumentResponse | null)
              : throwError(() => err),
          ),
        ),
  });

  private readonly hasProfileCv = computed(
    () => this.profileCv.hasValue() && this.profileCv.value() !== null,
  );

  /** Defaults to the profile CV when there is one, otherwise upload; user can switch. */
  readonly cvSource = linkedSignal<CvSource>(() => (this.hasProfileCv() ? 'profile' : 'upload'));
  readonly file = signal<File | null>(null);
  readonly fileError = signal<string | null>(null);
  /** Offered when the user has no profile CV yet. */
  readonly saveAsProfileCv = signal(true);

  readonly messageModel = signal({ message: '' });
  readonly messageForm = form(this.messageModel, (path) => {
    maxLength(path.message, MAX_MESSAGE_LENGTH);
  });

  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);

  readonly needsFile = computed(() => this.cvSource() === 'upload');
  readonly canSubmit = computed(
    () =>
      !this.submitting() &&
      !this.profileCv.isLoading() &&
      this.messageForm().valid() &&
      (!this.needsFile() || (this.file() !== null && this.fileError() === null)),
  );

  onFileSelected(input: HTMLInputElement): void {
    const file = input.files?.[0] ?? null;
    input.value = ''; // allow re-selecting the same file after removing it
    if (!file) return;
    const error = validateCvFile(file);
    this.fileError.set(error);
    this.file.set(error ? null : file);
  }

  removeFile(): void {
    this.file.set(null);
    this.fileError.set(null);
  }

  async submit(): Promise<void> {
    if (!this.canSubmit()) return;
    this.submitting.set(true);
    this.submitError.set(null);
    this.dialogRef.disableClose = true; // don't lose an in-flight submission to Esc / backdrop

    const file = this.needsFile() ? this.file() : null;
    const message = this.messageModel().message.trim();

    try {
      const application = await firstValueFrom(
        this.applicationsService.saveCurrentUserApplication(
          { jobListingId: this.data.job.id, applicantMessage: message || undefined },
          file ?? undefined,
        ),
      );

      // Saving the profile CV is a convenience: a failure must not undo the application.
      let profileCvSaveFailed = false;
      if (file && !this.hasProfileCv() && this.saveAsProfileCv()) {
        profileCvSaveFailed = await firstValueFrom(
          this.usersService.uploadCurrentUserCv(file),
        ).then(
          () => false,
          () => true,
        );
      }

      this.dialogRef.close({ status: 'applied', application, profileCvSaveFailed });
    } catch (err: unknown) {
      if (err instanceof HttpErrorResponse && err.status === 409) {
        this.dialogRef.close({ status: 'already-applied' });
        return;
      }
      this.submitError.set(this.describeError(err));
      this.submitting.set(false);
      this.dialogRef.disableClose = false;
    }
  }

  private describeError(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 404) return 'This job is no longer accepting applications.';
      if (err.status === 0)
        return 'Could not reach the server. Check your connection and try again.';
      const details = getApiErrorDetails(err);
      if (err.status === 400 && details) return details;
    }
    return 'Something went wrong while submitting your application. Please try again.';
  }
}
