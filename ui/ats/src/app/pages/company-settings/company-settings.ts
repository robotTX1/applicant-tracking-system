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
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { filter, firstValueFrom } from 'rxjs';
import { CompaniesService, CompanyRole } from '../../api';
import { CompanyFields } from '../../components/company-fields/company-fields';
import {
  DeleteCompanyDialog,
  DeleteCompanyDialogData,
} from '../../components/delete-company-dialog/delete-company-dialog';
import { HasPendingChanges } from '../../guards/pending-changes.guard';
import { CompanyRoleService } from '../../services/company-role.service';
import { CompanyStateService } from '../../services/company-state.service';
import { getApiErrorDetails } from '../../utils/api-error';
import {
  buildCompanyForm,
  normalizeWebsite,
  parseLocalDate,
  toCompanyRequest,
} from '../../utils/company-form';
import { canManageTeam } from '../../utils/company-roles';

@Component({
  selector: 'app-company-settings',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    CompanyFields,
  ],
  templateUrl: './company-settings.html',
  styleUrl: './company-settings.scss',
})
export class CompanySettings implements HasPendingChanges {
  private readonly companiesService = inject(CompaniesService);
  private readonly companyState = inject(CompanyStateService);
  private readonly companyRoles = inject(CompanyRoleService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly companyId = input.required({ transform: numberAttribute });

  readonly company = computed(
    () => this.companyState.companies().find((c) => c.id === this.companyId()) ?? null,
  );

  readonly role = resource({
    params: () => (this.company() ? this.companyId() : undefined),
    loader: ({ params }) => this.companyRoles.getRole(params),
  });

  /** UPDATE_COMPANY: owners and co-owners. DELETE_COMPANY: the owner only. */
  readonly canEdit = computed(() => canManageTeam(this.role.value()));
  readonly isOwner = computed(() => this.role.value() === CompanyRole.Owner);

  readonly form = buildCompanyForm(this.fb);
  readonly saving = signal(false);
  readonly saved = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    // Fill the form from the company (and again after a save), unless the user is editing
    effect(() => {
      const company = this.company();
      untracked(() => {
        if (!company || this.form.dirty) return;
        this.form.reset({
          name: company.name,
          foundingDate: parseLocalDate(company.foundingDate),
          websiteUrl: company.websiteUrl,
          description: company.description,
        });
      });
    });
    // Read-only for roles that can't edit
    effect(() => {
      if (this.role.hasValue() && !this.canEdit()) this.form.disable({ emitEvent: false });
      else this.form.enable({ emitEvent: false });
    });
  }

  hasPendingChanges(): boolean {
    return this.form.dirty && !this.saving();
  }

  async save(): Promise<void> {
    normalizeWebsite(this.form);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    try {
      const updated = await firstValueFrom(
        this.companiesService.updateCompany(this.companyId(), toCompanyRequest(this.form)),
      );
      this.form.markAsPristine();
      this.companyState.notifyCompanyUpdated(updated);
      this.snackBar.open('Company details saved.', 'Close', { duration: 4000 });
    } catch (err: unknown) {
      if (err instanceof HttpErrorResponse && err.status === 409) {
        this.form.controls.name.setErrors({ nameExists: true });
        this.error.set('Another company already uses this name.');
      } else {
        this.error.set(getApiErrorDetails(err) ?? 'Could not save the company details.');
      }
    } finally {
      this.saving.set(false);
    }
  }

  discard(): void {
    const company = this.company();
    if (!company) return;
    this.form.reset({
      name: company.name,
      foundingDate: parseLocalDate(company.foundingDate),
      websiteUrl: company.websiteUrl,
      description: company.description,
    });
    this.error.set(null);
  }

  deleteCompany(): void {
    const company = this.company();
    if (!company) return;
    this.dialog
      .open<DeleteCompanyDialog, DeleteCompanyDialogData, boolean>(DeleteCompanyDialog, {
        data: { company },
        width: '520px',
        maxWidth: '95vw',
      })
      .afterClosed()
      .pipe(filter((deleted) => deleted === true))
      .subscribe(async () => {
        // Nothing left to save: don't let the unsaved-changes guard block leaving
        this.form.markAsPristine();
        this.companyRoles.invalidate(company.id);
        this.companyState.notifyCompanyDeleted(company.id);
        await this.router.navigate(['/companies']);
        this.snackBar.open(`${company.name} was deleted.`, 'Close', { duration: 5000 });
      });
  }
}
