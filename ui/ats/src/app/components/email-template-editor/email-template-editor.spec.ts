import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import Keycloak from 'keycloak-js';
import { of, Subject, throwError } from 'rxjs';
import { CompanyEmailTemplateType, EmailTemplateResponse, EmailTemplatesService } from '../../api';
import { EmailTemplateEditor, EmailTemplateEditorData } from './email-template-editor';

describe('EmailTemplateEditor', () => {
  let fixture: ComponentFixture<EmailTemplateEditor>;
  let component: EmailTemplateEditor;
  let service: {
    saveEmailTemplate: ReturnType<typeof vi.fn>;
    updateEmailTemplate: ReturnType<typeof vi.fn>;
  };
  let dialogRef: {
    close: ReturnType<typeof vi.fn>;
    backdropClick: () => Subject<MouseEvent>;
    keydownEvents: () => Subject<KeyboardEvent>;
    disableClose: boolean;
  };
  let backdrop: Subject<MouseEvent>;
  let confirmResult: Subject<boolean>;
  let dialog: { open: ReturnType<typeof vi.fn> };

  const existing: EmailTemplateResponse = {
    id: 11,
    companyId: 7,
    type: CompanyEmailTemplateType.Decline,
    subject: 'About ${jobTitle}',
    body: 'Dear ${firstName},\nthanks.',
    createdAt: '',
    updatedAt: '',
  };

  async function setup(data: Partial<EmailTemplateEditorData> = {}) {
    service = {
      saveEmailTemplate: vi
        .fn()
        .mockImplementation((_c: number, body: object) => of({ id: 12, companyId: 7, ...body })),
      updateEmailTemplate: vi
        .fn()
        .mockImplementation((_c: number, id: number, body: object) =>
          of({ ...existing, id, ...body }),
        ),
    };
    backdrop = new Subject();
    dialogRef = {
      close: vi.fn(),
      backdropClick: () => backdrop,
      keydownEvents: () => new Subject(),
      disableClose: false,
    };
    confirmResult = new Subject();
    dialog = { open: vi.fn().mockReturnValue({ afterClosed: () => confirmResult }) };

    await TestBed.configureTestingModule({
      imports: [EmailTemplateEditor],
      providers: [
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            companyId: 7,
            companyName: 'Globex',
            type: CompanyEmailTemplateType.Offer,
            ...data,
          },
        },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MatDialog, useValue: dialog },
        { provide: EmailTemplatesService, useValue: service },
        {
          provide: Keycloak,
          useValue: { tokenParsed: { given_name: 'Elek', family_name: 'Teszt' } },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(EmailTemplateEditor);
    component = fixture.componentInstance;
    await fixture.whenStable();
  }

  const el = () => fixture.nativeElement as HTMLElement;

  it('creates a template with trimmed content', async () => {
    await setup();
    component.form.setValue({ subject: ' Offer for ${jobTitle} ', body: ' Hi ${firstName} ' });
    await component.save();
    expect(service.saveEmailTemplate).toHaveBeenCalledWith(7, {
      type: CompanyEmailTemplateType.Offer,
      subject: 'Offer for ${jobTitle}',
      body: 'Hi ${firstName}',
    });
    expect(dialogRef.close).toHaveBeenCalledWith(expect.objectContaining({ id: 12 }));
  });

  it('edits an existing template', async () => {
    await setup({ type: CompanyEmailTemplateType.Decline, template: existing });
    expect(el().textContent).toContain('Edit decline template');
    component.form.controls.body.setValue('New body');
    await component.save();
    expect(service.updateEmailTemplate).toHaveBeenCalledWith(7, 11, {
      type: CompanyEmailTemplateType.Decline,
      subject: 'About ${jobTitle}',
      body: 'New body',
    });
  });

  it('previews with the signed-in user, sample job details and the real company name', async () => {
    await setup({ template: existing });
    await fixture.whenStable();
    expect(el().querySelector('.email-subject')?.textContent).toContain(
      'About Senior Java Developer',
    );
    component.form.controls.body.setValue('${companyName} welcomes ${firstName}');
    await fixture.whenStable();
    expect(el().querySelector('.email-body')?.textContent).toContain('Globex welcomes Elek');
  });

  it('warns about placeholders the backend will not fill in', async () => {
    await setup();
    component.form.controls.body.setValue('Hi ${firstname}');
    await fixture.whenStable();
    expect(el().querySelector('.warning')?.textContent).toContain('${firstname}');
  });

  it('inserts a placeholder at the cursor of the last focused field', async () => {
    await setup();
    component.form.controls.body.setValue('Hello !');
    const textarea = el().querySelector('textarea')!;
    textarea.setSelectionRange(6, 6);
    component.rememberField('body');
    component.insert({ key: 'firstName', label: 'First name', sample: 'Anna' });
    expect(component.form.controls.body.value).toBe('Hello ${firstName}!');
    expect(component.form.dirty).toBe(true);
  });

  it('requires subject and message', async () => {
    await setup();
    component.form.setValue({ subject: '  ', body: '' });
    await component.save();
    expect(service.saveEmailTemplate).not.toHaveBeenCalled();
  });

  it('shows the server error and stays open', async () => {
    await setup();
    service.saveEmailTemplate.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { details: 'Limit reached' } })),
    );
    component.form.setValue({ subject: 'S', body: 'B' });
    await component.save();
    expect(component.error()).toBe('Limit reached');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('closes right away when nothing changed, asks first when there are edits', async () => {
    await setup();
    expect(dialogRef.disableClose).toBe(true);
    component.cancel();
    expect(dialogRef.close).toHaveBeenCalledTimes(1);

    component.form.controls.subject.setValue('Draft');
    component.form.markAsDirty();
    backdrop.next(new MouseEvent('click'));
    expect(dialog.open).toHaveBeenCalled();
    confirmResult.next(true);
    expect(dialogRef.close).toHaveBeenCalledTimes(2);
  });
});
