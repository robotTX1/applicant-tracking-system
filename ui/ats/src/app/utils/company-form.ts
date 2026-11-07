import { AbstractControl, FormBuilder, ValidationErrors, Validators } from '@angular/forms';
import { CompanyResponse, CompanySaveRequest } from '../api';

export const COMPANY_NAME_MAX = 100;
export const COMPANY_TEXT_MAX = 2000;

export function urlValidator(control: AbstractControl): ValidationErrors | null {
  if (!control.value) return null;
  const val = String(control.value).trim();
  const pattern = /^https?:\/\/[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}(:\d+)?(\/.*)?$/;
  return pattern.test(val) ? null : { invalidUrl: true };
}

export function notBlankValidator(control: AbstractControl): ValidationErrors | null {
  if (!control.value) return null;
  return String(control.value).trim().length > 0 ? null : { blank: true };
}

/** The register / settings form; `initial` fills it from an existing company. */
export function buildCompanyForm(fb: FormBuilder, initial?: CompanyResponse) {
  return fb.nonNullable.group({
    name: [
      initial?.name ?? '',
      [Validators.required, Validators.maxLength(COMPANY_NAME_MAX), notBlankValidator],
    ],
    foundingDate: [
      initial ? parseLocalDate(initial.foundingDate) : (null as Date | null),
      [Validators.required],
    ],
    websiteUrl: [
      initial?.websiteUrl ?? '',
      [Validators.required, Validators.maxLength(COMPANY_TEXT_MAX), urlValidator],
    ],
    description: [
      initial?.description ?? '',
      [Validators.required, Validators.maxLength(COMPANY_TEXT_MAX), notBlankValidator],
    ],
  });
}

export type CompanyForm = ReturnType<typeof buildCompanyForm>;

/** Adds https:// when the user typed a bare domain. */
export function normalizeWebsite(form: CompanyForm): void {
  const control = form.controls.websiteUrl;
  const val = control.value?.trim();
  if (val && !val.startsWith('http://') && !val.startsWith('https://')) {
    control.setValue('https://' + val, { emitEvent: true });
  }
}

/** `YYYY-MM-DD` as a local date (not UTC, which can shift the day). */
export function parseLocalDate(value: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value ?? '');
  return match ? new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3])) : null;
}

export function formatLocalDate(date: Date): string {
  const d = new Date(date);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Request body from a valid form (same shape for create and update). */
export function toCompanyRequest(form: CompanyForm): CompanySaveRequest {
  const v = form.getRawValue();
  return {
    name: v.name.trim(),
    foundingDate: formatLocalDate(v.foundingDate!),
    websiteUrl: v.websiteUrl.trim(),
    description: v.description.trim(),
  };
}
