import { Component, computed, DOCUMENT, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import Keycloak from 'keycloak-js';
import { catchError, filter, firstValueFrom, of, throwError } from 'rxjs';
import { DocumentResponse, UsersService } from '../../api';
import { ConfirmDialog, ConfirmDialogData } from '../../components/confirm-dialog/confirm-dialog';
import { getApiErrorDetails } from '../../utils/api-error';
import { CV_ACCEPT, validateCvFile } from '../../utils/cv-file';
import { saveBlob } from '../../utils/download';

type CvAction = 'upload' | 'download' | 'delete';

@Component({
  selector: 'app-profile',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class Profile {
  private readonly usersService = inject(UsersService);
  private readonly keycloak = inject(Keycloak);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly document = inject(DOCUMENT);

  readonly cvAccept = CV_ACCEPT;

  readonly user = rxResource({ stream: () => this.usersService.getCurrentUser() });

  /** The default CV, or null when the user hasn't uploaded one (404). */
  readonly cv = rxResource({
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

  readonly busy = signal<CvAction | null>(null);
  readonly fileError = signal<string | null>(null);

  readonly initials = computed(() => {
    const user = this.user.hasValue() ? this.user.value() : undefined;
    const initials = `${user?.firstName?.charAt(0) ?? ''}${user?.lastName?.charAt(0) ?? ''}`;
    return initials.toUpperCase() || '?';
  });

  /** Keycloak account console, where name, email and password are managed. */
  readonly accountUrl = this.safeAccountUrl();

  async onFileSelected(input: HTMLInputElement): Promise<void> {
    const file = input.files?.[0];
    input.value = ''; // allow choosing the same file again
    if (!file) return;

    const error = validateCvFile(file);
    this.fileError.set(error);
    if (error) return;

    const replacing = this.cv.hasValue() && this.cv.value() !== null;
    this.busy.set('upload');
    try {
      const uploaded = await firstValueFrom(this.usersService.uploadCurrentUserCv(file));
      this.cv.set(uploaded);
      this.snackBar.open(replacing ? 'Your CV was replaced.' : 'Your CV was uploaded.', 'Close', {
        duration: 4000,
      });
    } catch (err: unknown) {
      this.fileError.set(getApiErrorDetails(err) ?? 'Could not upload the CV. Please try again.');
    } finally {
      this.busy.set(null);
    }
  }

  async download(cv: DocumentResponse): Promise<void> {
    this.busy.set('download');
    try {
      const blob = await firstValueFrom(this.usersService.getCurrentUserCv());
      saveBlob(this.document, blob, cv.originalDocumentName || 'cv.pdf');
    } catch (err: unknown) {
      this.snackBar.open(getApiErrorDetails(err) ?? 'Could not download the CV.', 'Close', {
        duration: 5000,
      });
    } finally {
      this.busy.set(null);
    }
  }

  confirmDelete(cv: DocumentResponse): void {
    const data: ConfirmDialogData = {
      title: 'Delete your CV?',
      message: `"${cv.originalDocumentName}" will be removed from your profile. Applications you've already sent keep their own copy. You'll need to attach a CV the next time you apply.`,
      confirmText: 'Delete CV',
      cancelText: 'Keep CV',
      confirmColor: 'warn',
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data, width: '440px' })
      .afterClosed()
      .pipe(filter((confirmed) => confirmed === true))
      .subscribe(() => this.delete());
  }

  private async delete(): Promise<void> {
    this.busy.set('delete');
    try {
      await firstValueFrom(this.usersService.deleteCurrentUserCv());
      this.cv.set(null);
      this.fileError.set(null);
      this.snackBar.open('Your CV was deleted.', 'Close', { duration: 4000 });
    } catch (err: unknown) {
      this.snackBar.open(getApiErrorDetails(err) ?? 'Could not delete the CV.', 'Close', {
        duration: 5000,
      });
    } finally {
      this.busy.set(null);
    }
  }

  private safeAccountUrl(): string | null {
    try {
      return this.keycloak.createAccountUrl();
    } catch {
      return null; // Keycloak not initialised (e.g. tests without auth)
    }
  }
}
