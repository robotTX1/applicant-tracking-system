import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmDialog } from './confirm-dialog';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

describe('ConfirmDialog', () => {
  let component: ConfirmDialog;
  let fixture: ComponentFixture<ConfirmDialog>;

  const mockDialogRef = {
    close: vi.fn(),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConfirmDialog],
      providers: [
        { provide: MatDialogRef, useValue: mockDialogRef },
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            title: 'Discard Changes?',
            message: 'You have unsaved changes.',
            confirmText: 'Discard',
            cancelText: 'Stay',
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ConfirmDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and display configured text', () => {
    expect(component).toBeTruthy();
    expect(component.title).toBe('Discard Changes?');
    expect(component.message).toBe('You have unsaved changes.');
    expect(component.confirmText).toBe('Discard');
    expect(component.cancelText).toBe('Stay');

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('[mat-dialog-title]')?.textContent).toContain('Discard Changes?');
    expect(compiled.querySelector('mat-dialog-content p')?.textContent).toContain(
      'You have unsaved changes.',
    );
  });

  it('styles the confirm button as destructive for warn (the default)', () => {
    const button = (fixture.nativeElement as HTMLElement).querySelector('.confirm-button');
    expect(button?.classList).toContain('warn');
  });
});
