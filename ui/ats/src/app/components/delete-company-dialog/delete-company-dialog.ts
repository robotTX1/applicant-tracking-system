import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { firstValueFrom } from 'rxjs';
import { CompaniesService, CompanyResponse } from '../../api';
import { getApiErrorDetails } from '../../utils/api-error';

export interface DeleteCompanyDialogData {
  company: CompanyResponse;
}

/**
 * Deleting a company also deletes its jobs and, through them, every application, so the user
 * types the company name to confirm. Closes with `true` once the company is deleted.
 */
@Component({
  selector: 'app-delete-company-dialog',
  imports: [
    FormsModule,
    MatButtonModule,
    MatDialogActions,
    MatDialogContent,
    MatDialogTitle,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './delete-company-dialog.html',
  styleUrl: './delete-company-dialog.scss',
})
export class DeleteCompanyDialog {
  readonly data = inject<DeleteCompanyDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<DeleteCompanyDialog, boolean>>(MatDialogRef);
  private readonly companiesService = inject(CompaniesService);

  readonly typedName = signal('');
  readonly matches = computed(() => this.typedName().trim() === this.data.company.name.trim());
  readonly deleting = signal(false);
  readonly error = signal<string | null>(null);

  async confirm(): Promise<void> {
    if (!this.matches() || this.deleting()) return;
    this.deleting.set(true);
    this.error.set(null);
    this.dialogRef.disableClose = true;
    try {
      await firstValueFrom(this.companiesService.deleteCompany(this.data.company.id));
      this.dialogRef.close(true);
    } catch (err: unknown) {
      this.error.set(getApiErrorDetails(err) ?? 'Could not delete the company.');
      this.dialogRef.disableClose = false;
    } finally {
      this.deleting.set(false);
    }
  }

  cancel(): void {
    this.dialogRef.close(false);
  }
}
