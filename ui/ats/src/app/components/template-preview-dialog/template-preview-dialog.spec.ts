import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TemplatePreviewDialog, TemplatePreviewDialogData } from './template-preview-dialog';
import { CompanyEmailTemplateType } from '../../api';

describe('TemplatePreviewDialog', () => {
  let component: TemplatePreviewDialog;
  let fixture: ComponentFixture<TemplatePreviewDialog>;

  const mockDialogRef = {
    close: vi.fn(),
  };

  const mockOfferData: TemplatePreviewDialogData = {
    type: CompanyEmailTemplateType.Offer,
    subject: 'Offer of Employment: {{job_title}}',
    body: 'Dear {{candidate_name}}, we are excited to offer you the role!',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TemplatePreviewDialog],
      providers: [
        { provide: MatDialogRef, useValue: mockDialogRef },
        { provide: MAT_DIALOG_DATA, useValue: mockOfferData },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TemplatePreviewDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and display offer template data', () => {
    expect(component).toBeTruthy();
    expect(component.isOffer).toBe(true);
    expect(component.typeLabel).toBe('Offer Template');
    expect(component.subject).toBe('Offer of Employment: {{job_title}}');
    expect(component.body).toBe('Dear {{candidate_name}}, we are excited to offer you the role!');

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.dialog-title')?.textContent).toContain('Offer Template Preview');
    expect(el.querySelector('.subject-text')?.textContent).toContain(
      'Offer of Employment: {{job_title}}',
    );
    expect(el.querySelector('.body-text')?.textContent).toContain(
      'Dear {{candidate_name}}, we are excited to offer you the role!',
    );
  });

  it('should display decline template data correctly when initialized with decline type', () => {
    const declineData: TemplatePreviewDialogData = {
      type: CompanyEmailTemplateType.Decline,
      subject: 'Application Update: {{job_title}}',
      body: 'Thank you for your interest in our company.',
    };

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [TemplatePreviewDialog],
      providers: [
        { provide: MatDialogRef, useValue: mockDialogRef },
        { provide: MAT_DIALOG_DATA, useValue: declineData },
      ],
    }).compileComponents();

    const declineFixture = TestBed.createComponent(TemplatePreviewDialog);
    const declineComp = declineFixture.componentInstance;
    declineFixture.detectChanges();

    expect(declineComp.isOffer).toBe(false);
    expect(declineComp.typeLabel).toBe('Decline Template');
    const el = declineFixture.nativeElement as HTMLElement;
    expect(el.querySelector('.dialog-title')?.textContent).toContain('Decline Template Preview');
    expect(el.querySelector('.badge-decline')?.textContent).toContain('DECLINE');
  });
});
