import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { CompaniesService } from '../../api';
import { DeleteCompanyDialog } from './delete-company-dialog';

describe('DeleteCompanyDialog', () => {
  let fixture: ComponentFixture<DeleteCompanyDialog>;
  let component: DeleteCompanyDialog;
  let companiesService: { deleteCompany: ReturnType<typeof vi.fn> };
  let dialogRef: { close: ReturnType<typeof vi.fn>; disableClose: boolean };

  beforeEach(async () => {
    companiesService = { deleteCompany: vi.fn().mockReturnValue(of(undefined)) };
    dialogRef = { close: vi.fn(), disableClose: false };
    await TestBed.configureTestingModule({
      imports: [DeleteCompanyDialog],
      providers: [
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            company: {
              id: 7,
              name: 'Acme Corp',
              foundingDate: '',
              websiteUrl: '',
              description: '',
            },
          },
        },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: CompaniesService, useValue: companiesService },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(DeleteCompanyDialog);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  const deleteButton = () =>
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.delete-button')!;

  it('stays disabled until the exact company name is typed', async () => {
    expect(deleteButton().disabled).toBe(true);
    component.typedName.set('acme corp');
    await fixture.whenStable();
    expect(deleteButton().disabled).toBe(true);
    await component.confirm();
    expect(companiesService.deleteCompany).not.toHaveBeenCalled();

    component.typedName.set(' Acme Corp ');
    await fixture.whenStable();
    expect(deleteButton().disabled).toBe(false);
  });

  it('deletes and closes with true', async () => {
    component.typedName.set('Acme Corp');
    await component.confirm();
    expect(companiesService.deleteCompany).toHaveBeenCalledWith(7);
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('shows the backend error and stays open', async () => {
    companiesService.deleteCompany.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403, error: { details: 'Owner only' } })),
    );
    component.typedName.set('Acme Corp');
    await component.confirm();
    expect(component.error()).toBe('Owner only');
    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(dialogRef.disableClose).toBe(false);
  });
});
