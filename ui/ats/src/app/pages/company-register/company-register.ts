import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';
import { CompaniesService, CompanySaveRequest } from '../../api';
import { CompanyStateService } from '../../services/company-state.service';
import { ResourceLimit } from '../../enums/resource-limit.enum';
import { HasPendingChanges } from '../../guards/pending-changes.guard';
import { HttpErrorResponse } from '@angular/common/http';
import { getApiErrorDetails, getApiErrorTitle } from '../../utils/api-error';
import { CompanyFields } from '../../components/company-fields/company-fields';
import { buildCompanyForm, normalizeWebsite, toCompanyRequest } from '../../utils/company-form';

@Component({
  selector: 'app-company-register',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    CompanyFields,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
  ],
  templateUrl: './company-register.html',
  styleUrl: './company-register.scss',
})
export class CompanyRegister implements HasPendingChanges {
  private readonly fb = inject(FormBuilder);
  private readonly companiesService = inject(CompaniesService);
  protected readonly companyState = inject(CompanyStateService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  readonly maxCompanies = ResourceLimit.MAX_COMPANIES;

  readonly isSubmitting = signal<boolean>(false);
  readonly isSubmitted = signal<boolean>(false);
  readonly serverError = signal<string | null>(null);

  readonly ownedCount = computed(() => this.companyState.ownedCompaniesCount());
  readonly limitReached = computed(() => !this.companyState.canCreateCompany());

  readonly form = buildCompanyForm(this.fb);

  constructor() {
    if (typeof this.companyState.init === 'function') {
      this.companyState.init().catch((err: unknown) => {
        console.error('Failed to initialize company state in company register:', err);
      });
    }
  }

  hasPendingChanges(): boolean {
    return this.form.dirty && !this.isSubmitting() && !this.isSubmitted();
  }

  normalizeWebsite(): void {
    normalizeWebsite(this.form);
  }

  async onSubmit(): Promise<void> {
    if (this.limitReached()) {
      this.serverError.set(
        `You have reached the maximum limit of ${this.maxCompanies} companies per account.`,
      );
      return;
    }

    this.normalizeWebsite();

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const payload: CompanySaveRequest = toCompanyRequest(this.form);

    this.isSubmitting.set(true);
    this.serverError.set(null);

    try {
      const response = await firstValueFrom(this.companiesService.saveCompany(payload));
      this.isSubmitted.set(true);
      this.companyState.notifyCompanyCreated(response);

      this.snackBar.open(`Company "${response.name}" created successfully!`, 'Close', {
        duration: 4000,
        horizontalPosition: 'end',
        verticalPosition: 'bottom',
      });

      await this.router.navigate(['/jobs/new']);
    } catch (err: unknown) {
      this.isSubmitting.set(false);
      const status = err instanceof HttpErrorResponse ? err.status : undefined;
      const details = getApiErrorDetails(err);
      const title = getApiErrorTitle(err);

      if (status === 409) {
        this.form.controls.name.setErrors({ nameExists: true });
        this.serverError.set('A company with this name already exists.');
      } else if (
        status === 400 &&
        (title?.toLowerCase().includes('limit') || details?.toLowerCase().includes('limit'))
      ) {
        this.serverError.set(
          `You have reached the maximum allowed limit of ${this.maxCompanies} companies.`,
        );
      } else {
        this.serverError.set(details ?? 'An error occurred while creating the company.');
      }
    }
  }

  cancel(): void {
    this.router.navigate(['/companies']);
  }
}
