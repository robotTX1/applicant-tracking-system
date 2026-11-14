import {
  Component,
  computed,
  inject,
  input,
  numberAttribute,
  resource,
  signal,
} from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import Keycloak from 'keycloak-js';
import { catchError, filter, firstValueFrom, map, of } from 'rxjs';
import {
  CompanyEmailTemplateType,
  EmailTemplateResponse,
  EmailTemplatesService,
  JobListingResponse,
  JobListingsService,
} from '../../api';
import { ConfirmDialog, ConfirmDialogData } from '../../components/confirm-dialog/confirm-dialog';
import {
  EmailTemplateEditor,
  EmailTemplateEditorData,
} from '../../components/email-template-editor/email-template-editor';
import { canManageJobs, CompanyRoleService } from '../../services/company-role.service';
import { CompanyStateService } from '../../services/company-state.service';
import { getApiErrorDetails } from '../../utils/api-error';
import {
  MAX_TEMPLATES_PER_TYPE,
  previewPersonFromToken,
  renderTemplate,
  sampleValues,
  TEMPLATE_TYPE_LABELS,
} from '../../utils/email-template';
import { eq } from '../../utils/rsql';

interface TemplateSection {
  type: CompanyEmailTemplateType;
  title: string;
  description: string;
  icon: string;
}

const SECTIONS: readonly TemplateSection[] = [
  {
    type: CompanyEmailTemplateType.Offer,
    title: 'Offer emails',
    description: 'Sent when you move a candidate to Offer.',
    icon: 'celebration',
  },
  {
    type: CompanyEmailTemplateType.Decline,
    title: 'Decline emails',
    description: 'Sent when you move a candidate to Declined.',
    icon: 'mark_email_unread',
  },
];

@Component({
  selector: 'app-company-email-templates',
  imports: [
    LowerCasePipe,
    RouterLink,
    MatButtonModule,
    MatDividerModule,
    MatIconModule,
    MatMenuModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
  templateUrl: './company-email-templates.html',
  styleUrl: './company-email-templates.scss',
})
export class CompanyEmailTemplates {
  private readonly templatesService = inject(EmailTemplatesService);
  private readonly jobListingsService = inject(JobListingsService);
  private readonly companyState = inject(CompanyStateService);
  private readonly companyRoles = inject(CompanyRoleService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly keycloak = inject(Keycloak, { optional: true });

  readonly companyId = input.required({ transform: numberAttribute });

  readonly sections = SECTIONS;
  readonly maxPerType = MAX_TEMPLATES_PER_TYPE;

  readonly company = computed(
    () => this.companyState.companies().find((c) => c.id === this.companyId()) ?? null,
  );

  readonly role = resource({
    params: () => (this.company() ? this.companyId() : undefined),
    loader: ({ params }) => this.companyRoles.getRole(params),
  });

  /** MANAGE_EMAIL_TEMPLATES has the same roles as job management. */
  readonly canManage = computed(() => canManageJobs(this.role.value()));
  readonly noAccess = computed(() => this.role.hasValue() && !this.canManage());

  readonly templates = rxResource({
    params: () => (this.canManage() ? this.companyId() : undefined),
    stream: ({ params }) =>
      this.templatesService
        .getEmailTemplates(params, { sort: 'createdAt,asc', pageSize: 100 })
        .pipe(map((res) => res.content)),
  });

  /** Jobs per template id, for "Used by" and to block deleting templates in use. */
  readonly usage = rxResource({
    params: () => (this.canManage() ? this.companyId() : undefined),
    stream: ({ params }) =>
      this.jobListingsService
        .getJobListings({ query: eq('companyId', params), pageSize: 100 })
        .pipe(
          map((res) => {
            const byTemplate = new Map<number, JobListingResponse[]>();
            for (const job of res.content) {
              for (const id of [job.offerEmailTemplateId, job.declineEmailTemplateId]) {
                if (id == null) continue;
                byTemplate.set(id, [...(byTemplate.get(id) ?? []), job]);
              }
            }
            return byTemplate;
          }),
          // Unknown usage only means the backend decides whether a delete is allowed
          catchError(() => of(null)),
        ),
  });

  readonly byType = computed(() => {
    const list = this.templates.hasValue() ? this.templates.value() : [];
    return new Map(SECTIONS.map((s) => [s.type, list.filter((t) => t.type === s.type)]));
  });

  readonly deletingId = signal<number | null>(null);

  private readonly previewPerson = previewPersonFromToken(this.keycloak?.tokenParsed);
  private readonly samples = computed(() => sampleValues(this.company()?.name, this.previewPerson));

  usedBy(template: EmailTemplateResponse): JobListingResponse[] {
    return this.usage.value()?.get(template.id) ?? [];
  }

  usedByLabel(template: EmailTemplateResponse): string {
    const jobs = this.usedBy(template);
    return jobs.length === 1 ? 'Used by 1 job' : `Used by ${jobs.length} jobs`;
  }

  usedByTooltip(template: EmailTemplateResponse): string {
    return this.usedBy(template)
      .map((j) => j.title)
      .join(', ');
  }

  preview(text: string): string {
    return renderTemplate(text, this.samples());
  }

  canAdd(type: CompanyEmailTemplateType): boolean {
    return (this.byType().get(type)?.length ?? 0) < MAX_TEMPLATES_PER_TYPE;
  }

  create(type: CompanyEmailTemplateType, initial?: { subject: string; body: string }): void {
    this.openEditor({ type, initial });
  }

  edit(template: EmailTemplateResponse): void {
    this.openEditor({ type: template.type, template });
  }

  duplicate(template: EmailTemplateResponse): void {
    this.create(template.type, {
      subject: `${template.subject} (copy)`.slice(0, 255),
      body: template.body,
    });
  }

  remove(template: EmailTemplateResponse): void {
    const data: ConfirmDialogData = {
      title: 'Delete this template?',
      message: `"${template.subject}" will be deleted. This can't be undone.`,
      confirmText: 'Delete',
      cancelText: 'Keep it',
      confirmColor: 'warn',
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data, width: '420px' })
      .afterClosed()
      .pipe(filter((confirmed) => confirmed === true))
      .subscribe(async () => {
        this.deletingId.set(template.id);
        try {
          await firstValueFrom(
            this.templatesService.deleteEmailTemplate(this.companyId(), template.id),
          );
          this.templates.update((list) => list?.filter((t) => t.id !== template.id));
          this.snackBar.open('Template deleted.', 'Close', { duration: 4000 });
        } catch (err: unknown) {
          this.snackBar.open(getApiErrorDetails(err) ?? 'Could not delete the template.', 'Close', {
            duration: 5000,
          });
        } finally {
          this.deletingId.set(null);
        }
      });
  }

  private openEditor(partial: Omit<EmailTemplateEditorData, 'companyId' | 'companyName'>): void {
    const company = this.company();
    if (!company) return;
    const data: EmailTemplateEditorData = {
      companyId: company.id,
      companyName: company.name,
      ...partial,
    };
    this.dialog
      .open<EmailTemplateEditor, EmailTemplateEditorData, EmailTemplateResponse>(
        EmailTemplateEditor,
        { data, width: '1000px', maxWidth: '95vw', autoFocus: 'first-tabbable' },
      )
      .afterClosed()
      .pipe(filter((saved): saved is EmailTemplateResponse => !!saved))
      .subscribe((saved) => {
        const isNew = !partial.template;
        this.templates.update((list) =>
          isNew ? [...(list ?? []), saved] : list?.map((t) => (t.id === saved.id ? saved : t)),
        );
        this.snackBar.open(
          isNew
            ? `${TEMPLATE_TYPE_LABELS[saved.type]} template created. Choose it when you post or edit a job.`
            : 'Template saved. Jobs using it send the new version from now on.',
          'Close',
          { duration: 5000 },
        );
      });
  }
}
