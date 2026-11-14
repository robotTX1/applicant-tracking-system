import { Component, computed, ElementRef, inject, signal, viewChild } from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { TextFieldModule } from '@angular/cdk/text-field';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogActions,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import Keycloak from 'keycloak-js';
import { filter, firstValueFrom, map, startWith } from 'rxjs';
import { CompanyEmailTemplateType, EmailTemplateResponse, EmailTemplatesService } from '../../api';
import { getApiErrorDetails } from '../../utils/api-error';
import {
  BODY_MAX_LENGTH,
  placeholderToken,
  previewPersonFromToken,
  renderTemplate,
  sampleValues,
  SUBJECT_MAX_LENGTH,
  TEMPLATE_PLACEHOLDERS,
  TEMPLATE_TYPE_LABELS,
  TemplatePlaceholder,
  unknownPlaceholders,
} from '../../utils/email-template';
import { ConfirmDialog, ConfirmDialogData } from '../confirm-dialog/confirm-dialog';

export interface EmailTemplateEditorData {
  companyId: number;
  companyName: string;
  type: CompanyEmailTemplateType;
  /** The template to edit; omitted when creating. */
  template?: EmailTemplateResponse;
  /** Starting content for a new template (e.g. when duplicating). */
  initial?: { subject: string; body: string };
}

function notBlank(control: AbstractControl): ValidationErrors | null {
  return String(control.value ?? '').trim() ? null : { blank: true };
}

type Field = 'subject' | 'body';

@Component({
  selector: 'app-email-template-editor',
  imports: [
    LowerCasePipe,
    ReactiveFormsModule,
    TextFieldModule,
    MatButtonModule,
    MatDialogActions,
    MatDialogContent,
    MatDialogTitle,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
  templateUrl: './email-template-editor.html',
  styleUrl: './email-template-editor.scss',
})
export class EmailTemplateEditor {
  readonly data = inject<EmailTemplateEditorData>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<EmailTemplateEditor, EmailTemplateResponse>>(MatDialogRef);
  private readonly dialog = inject(MatDialog);
  private readonly templatesService = inject(EmailTemplatesService);
  private readonly fb = inject(FormBuilder);
  private readonly keycloak = inject(Keycloak, { optional: true });

  private readonly subjectInput = viewChild<ElementRef<HTMLInputElement>>('subjectInput');
  private readonly bodyInput = viewChild<ElementRef<HTMLTextAreaElement>>('bodyInput');

  readonly placeholders = TEMPLATE_PLACEHOLDERS;
  readonly subjectMax = SUBJECT_MAX_LENGTH;
  readonly bodyMax = BODY_MAX_LENGTH;
  readonly isEdit = !!this.data.template;
  readonly typeLabel = TEMPLATE_TYPE_LABELS[this.data.type];
  readonly isOffer = this.data.type === CompanyEmailTemplateType.Offer;

  readonly form = this.fb.nonNullable.group({
    subject: [
      this.data.template?.subject ?? this.data.initial?.subject ?? '',
      [Validators.required, Validators.maxLength(SUBJECT_MAX_LENGTH), notBlank],
    ],
    body: [
      this.data.template?.body ?? this.data.initial?.body ?? '',
      [Validators.required, Validators.maxLength(BODY_MAX_LENGTH), notBlank],
    ],
  });

  readonly value = toSignal(
    this.form.valueChanges.pipe(
      startWith(null),
      map(() => this.form.getRawValue()),
    ),
    { requireSync: true },
  );

  private readonly samples = sampleValues(
    this.data.companyName,
    previewPersonFromToken(this.keycloak?.tokenParsed),
  );
  readonly previewSubject = computed(() => renderTemplate(this.value().subject, this.samples));
  readonly previewBody = computed(() => renderTemplate(this.value().body, this.samples));
  readonly unknown = computed(() =>
    unknownPlaceholders(`${this.value().subject}\n${this.value().body}`),
  );

  /** Where a placeholder chip inserts: the field that had focus last. */
  private lastField: Field = 'body';
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    // Unsaved edits shouldn't vanish on Esc or a backdrop click
    this.dialogRef.disableClose = true;
    this.dialogRef.backdropClick().subscribe(() => this.cancel());
    this.dialogRef
      .keydownEvents()
      .pipe(filter((e) => e.key === 'Escape'))
      .subscribe(() => this.cancel());
  }

  rememberField(field: Field): void {
    this.lastField = field;
  }

  insert(placeholder: TemplatePlaceholder): void {
    const field = this.lastField;
    const el = (field === 'subject' ? this.subjectInput() : this.bodyInput())?.nativeElement;
    const control = this.form.controls[field];
    const token = placeholderToken(placeholder.key);
    const value = control.value;
    const start = el?.selectionStart ?? value.length;
    const end = el?.selectionEnd ?? value.length;
    control.setValue(value.slice(0, start) + token + value.slice(end));
    control.markAsDirty();
    if (el) {
      el.focus();
      const caret = start + token.length;
      queueMicrotask(() => el.setSelectionRange(caret, caret));
    }
  }

  async save(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const body = {
      type: this.data.type,
      subject: this.form.controls.subject.value.trim(),
      body: this.form.controls.body.value.trim(),
    };
    this.saving.set(true);
    this.error.set(null);
    try {
      const saved = await firstValueFrom(
        this.data.template
          ? this.templatesService.updateEmailTemplate(
              this.data.companyId,
              this.data.template.id,
              body,
            )
          : this.templatesService.saveEmailTemplate(this.data.companyId, body),
      );
      this.dialogRef.close(saved);
    } catch (err: unknown) {
      this.error.set(getApiErrorDetails(err) ?? 'Could not save the template.');
    } finally {
      this.saving.set(false);
    }
  }

  cancel(): void {
    if (this.saving()) return;
    if (!this.form.dirty) {
      this.dialogRef.close();
      return;
    }
    const data: ConfirmDialogData = {
      title: 'Discard changes?',
      message: "Your changes to this template haven't been saved.",
      confirmText: 'Discard',
      cancelText: 'Keep editing',
      confirmColor: 'warn',
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data, width: '400px' })
      .afterClosed()
      .pipe(filter((confirmed) => confirmed === true))
      .subscribe(() => this.dialogRef.close());
  }
}
