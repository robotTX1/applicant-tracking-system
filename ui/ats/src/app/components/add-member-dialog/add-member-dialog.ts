import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { firstValueFrom } from 'rxjs';
import {
  CompanyRole,
  EmployeeResponse,
  EmployeesService,
  UserResponse,
  UsersService,
} from '../../api';
import { getApiErrorDetails } from '../../utils/api-error';
import { ASSIGNABLE_ROLES, ROLE_INFO } from '../../utils/company-roles';

export interface AddMemberDialogData {
  companyId: number;
  companyName: string;
  /** Active members, to catch "already in the team" before calling the API. */
  memberUserIds: ReadonlySet<string>;
}

/** Emails are often pasted with surrounding spaces: validate the trimmed value. */
function trimmedEmail(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value?.trim() ?? '';
  return value ? Validators.email({ value } as AbstractControl) : null;
}

type LookupState =
  | { kind: 'idle' }
  | { kind: 'searching' }
  | { kind: 'found'; user: UserResponse }
  | { kind: 'not-found'; email: string }
  | { kind: 'already-member'; user: UserResponse }
  | { kind: 'error'; message: string };

@Component({
  selector: 'app-add-member-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatRadioModule,
  ],
  templateUrl: './add-member-dialog.html',
  styleUrl: './add-member-dialog.scss',
})
export class AddMemberDialog {
  readonly data = inject<AddMemberDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<AddMemberDialog, EmployeeResponse>>(MatDialogRef);
  private readonly usersService = inject(UsersService);
  private readonly employeesService = inject(EmployeesService);
  private readonly fb = inject(FormBuilder);

  readonly roles = ASSIGNABLE_ROLES;
  readonly roleInfo = ROLE_INFO;

  readonly lookup = signal<LookupState>({ kind: 'idle' });
  readonly saving = signal(false);
  readonly saveError = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, trimmedEmail, Validators.maxLength(255)]],
    role: [CompanyRole.HrManager as CompanyRole, Validators.required],
  });

  constructor() {
    // A changed email invalidates the previous lookup
    this.form.controls.email.valueChanges.subscribe(() => {
      if (this.lookup().kind !== 'idle') this.lookup.set({ kind: 'idle' });
      this.saveError.set(null);
    });
  }

  /**
   * Finds the account by exact email (the backend offers no search, so other users' details stay
   * private). People need to have signed in to the ATS once, so their account exists.
   */
  async findUser(): Promise<void> {
    const control = this.form.controls.email;
    if (control.invalid) {
      control.markAsTouched();
      return;
    }
    const email = control.value.trim();
    this.lookup.set({ kind: 'searching' });
    try {
      const user = await firstValueFrom(this.usersService.lookupUser(email));
      this.lookup.set(
        this.data.memberUserIds.has(user.id)
          ? { kind: 'already-member', user }
          : { kind: 'found', user },
      );
    } catch (err: unknown) {
      if (err instanceof HttpErrorResponse && err.status === 404) {
        this.lookup.set({ kind: 'not-found', email });
        return;
      }
      this.lookup.set({
        kind: 'error',
        message: getApiErrorDetails(err) ?? 'Could not look up this email. Try again.',
      });
    }
  }

  async add(): Promise<void> {
    const state = this.lookup();
    if (state.kind !== 'found' || this.form.invalid) return;
    this.saving.set(true);
    this.saveError.set(null);
    try {
      const employee = await firstValueFrom(
        this.employeesService.saveEmployee(this.data.companyId, {
          userId: state.user.id,
          role: this.form.controls.role.value,
        }),
      );
      this.dialogRef.close(employee);
    } catch (err: unknown) {
      this.saveError.set(
        err instanceof HttpErrorResponse && err.status === 409
          ? `${state.user.firstName} is already a member of ${this.data.companyName}.`
          : (getApiErrorDetails(err) ?? 'Could not add this member.'),
      );
    } finally {
      this.saving.set(false);
    }
  }
}
