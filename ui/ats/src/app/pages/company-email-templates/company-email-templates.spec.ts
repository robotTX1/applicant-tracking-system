import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import {
  CompanyEmailTemplateType as T,
  CompanyResponse,
  CompanyRole,
  EmailTemplateResponse,
  EmailTemplatesService,
  JobListingsService,
} from '../../api';
import { EmailTemplateEditor } from '../../components/email-template-editor/email-template-editor';
import { CompanyRoleService } from '../../services/company-role.service';
import { CompanyStateService } from '../../services/company-state.service';
import { CompanyEmailTemplates } from './company-email-templates';

const company: CompanyResponse = {
  id: 7,
  name: 'Globex',
  foundingDate: '',
  websiteUrl: '',
  description: '',
};

const template = (id: number, type: T, subject = `Subject ${id}`): EmailTemplateResponse => ({
  id,
  companyId: 7,
  type,
  subject,
  body: 'Dear ${firstName}, about ${jobTitle} at ${companyName}.',
  createdAt: '',
  updatedAt: '',
});

describe('CompanyEmailTemplates', () => {
  let templatesService: {
    getEmailTemplates: ReturnType<typeof vi.fn>;
    deleteEmailTemplate: ReturnType<typeof vi.fn>;
  };
  let jobListingsService: { getJobListings: ReturnType<typeof vi.fn> };
  let role: CompanyRole;
  let dialogResult: Subject<unknown>;
  let dialog: { open: ReturnType<typeof vi.fn> };
  let snackBar: { open: ReturnType<typeof vi.fn> };

  const templates = [
    template(1, T.Offer, 'Offer: ${jobTitle}'),
    template(2, T.Decline),
    template(3, T.Offer),
    template(4, T.Offer),
  ];

  beforeEach(() => {
    role = CompanyRole.HrManager;
    templatesService = {
      getEmailTemplates: vi.fn().mockReturnValue(
        of({
          content: templates,
          page: { size: 100, number: 0, totalElements: 4, totalPages: 1 },
        }),
      ),
      deleteEmailTemplate: vi.fn().mockReturnValue(of(undefined)),
    };
    jobListingsService = {
      getJobListings: vi.fn().mockReturnValue(
        of({
          content: [
            { id: 1, title: 'Java Dev', offerEmailTemplateId: 1, declineEmailTemplateId: 2 },
            { id: 2, title: 'QA', offerEmailTemplateId: 1, declineEmailTemplateId: 2 },
          ],
          page: { size: 100, number: 0, totalElements: 2, totalPages: 1 },
        }),
      ),
    };
    dialogResult = new Subject();
    dialog = { open: vi.fn().mockReturnValue({ afterClosed: () => dialogResult }) };
    snackBar = { open: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [{ path: 'companies/:companyId/email-templates', component: CompanyEmailTemplates }],
          withComponentInputBinding(),
        ),
        { provide: EmailTemplatesService, useValue: templatesService },
        { provide: JobListingsService, useValue: jobListingsService },
        { provide: CompanyStateService, useValue: { companies: signal([company]) } },
        { provide: CompanyRoleService, useValue: { getRole: () => Promise.resolve(role) } },
        { provide: MatDialog, useValue: dialog },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
  });

  async function render(url = '/companies/7/email-templates') {
    const harness = await RouterTestingHarness.create(url);
    await harness.fixture.whenStable();
    return {
      harness,
      el: harness.routeNativeElement as HTMLElement,
      component: harness.routeDebugElement!.componentInstance as CompanyEmailTemplates,
    };
  }

  const section = (el: HTMLElement, type: T) =>
    el.querySelector<HTMLElement>(`section[aria-labelledby="section-${type}"]`)!;
  const cards = (el: HTMLElement, type: T) => [
    ...section(el, type).querySelectorAll<HTMLElement>('.template-card'),
  ];

  it('groups templates by type with counts and filled-in previews', async () => {
    const { el } = await render();
    expect(cards(el, T.Offer).length).toBe(3);
    expect(cards(el, T.Decline).length).toBe(1);
    expect(section(el, T.Offer).querySelector('.count')?.textContent).toContain('3 / 3');
    expect(cards(el, T.Offer)[0].querySelector('.template-subject')?.textContent).toContain(
      'Offer: Senior Java Developer',
    );
    expect(cards(el, T.Offer)[0].textContent).toContain('Globex');
  });

  it('shows which jobs use a template', async () => {
    const { el } = await render();
    expect(jobListingsService.getJobListings).toHaveBeenCalledWith({
      query: "companyId=='7'",
      pageSize: 100,
    });
    expect(cards(el, T.Offer)[0].textContent).toContain('Used by 2 jobs');
    expect(cards(el, T.Offer)[1].textContent).toContain('Not used by any job');
  });

  it('disables New template at the limit of 3 per type', async () => {
    const { el, component } = await render();
    const newButton = (type: T) =>
      section(el, type).querySelector<HTMLButtonElement>('.section-header button')!;
    expect(newButton(T.Offer).disabled).toBe(true);
    expect(newButton(T.Decline).disabled).toBe(false);
    expect(component.canAdd(T.Offer)).toBe(false);
  });

  it('opens the editor and adds the created template', async () => {
    const { el, harness, component } = await render();
    component.create(T.Decline);
    expect(dialog.open).toHaveBeenCalledWith(
      EmailTemplateEditor,
      expect.objectContaining({
        data: expect.objectContaining({ companyId: 7, companyName: 'Globex', type: T.Decline }),
      }),
    );
    dialogResult.next(template(9, T.Decline, 'Fresh'));
    await harness.fixture.whenStable();
    expect(cards(el, T.Decline).length).toBe(2);
  });

  it('duplicates with a "(copy)" subject', async () => {
    const { component } = await render();
    component.duplicate(templates[1]);
    expect(dialog.open.mock.lastCall![1].data.initial).toEqual({
      subject: 'Subject 2 (copy)',
      body: templates[1].body,
    });
    expect(dialog.open.mock.lastCall![1].data.template).toBeUndefined();
  });

  it('deletes an unused template after confirmation', async () => {
    const { el, harness, component } = await render();
    component.remove(templates[2]);
    dialogResult.next(true);
    await harness.fixture.whenStable();
    expect(templatesService.deleteEmailTemplate).toHaveBeenCalledWith(7, 3);
    expect(cards(el, T.Offer).length).toBe(2);
  });

  it('reports a rejected delete', async () => {
    templatesService.deleteEmailTemplate.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { details: 'In use' } })),
    );
    const { harness, component } = await render();
    component.remove(templates[2]);
    dialogResult.next(true);
    await harness.fixture.whenStable();
    expect(snackBar.open).toHaveBeenCalledWith('In use', 'Close', expect.anything());
  });

  it('explains that technical interviewers cannot manage templates', async () => {
    role = CompanyRole.TechnicalInterviewer;
    const { el } = await render();
    expect(el.textContent).toContain('Only owners, co-owners and HR managers');
    expect(templatesService.getEmailTemplates).not.toHaveBeenCalled();
  });

  it('shows not found for a company the user is not in', async () => {
    const { el } = await render('/companies/99/email-templates');
    expect(el.textContent).toContain('Company not found');
  });
});
