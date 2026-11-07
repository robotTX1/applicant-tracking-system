import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter, Router, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { of, Subject, throwError } from 'rxjs';
import { CompaniesService, CompanyResponse, CompanyRole } from '../../api';
import { DeleteCompanyDialog } from '../../components/delete-company-dialog/delete-company-dialog';
import { CompanyRoleService } from '../../services/company-role.service';
import { CompanyStateService } from '../../services/company-state.service';
import { CompanySettings } from './company-settings';

const company: CompanyResponse = {
  id: 7,
  name: 'Acme',
  foundingDate: '2020-03-15',
  websiteUrl: 'https://acme.com',
  description: 'We make things.',
};

describe('CompanySettings', () => {
  let companiesService: { updateCompany: ReturnType<typeof vi.fn> };
  let role: CompanyRole;
  let companyState: {
    companies: ReturnType<typeof signal<CompanyResponse[]>>;
    notifyCompanyUpdated: ReturnType<typeof vi.fn>;
    notifyCompanyDeleted: ReturnType<typeof vi.fn>;
  };
  let dialogResult: Subject<boolean>;
  let dialog: { open: ReturnType<typeof vi.fn> };
  let snackBar: { open: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    role = CompanyRole.Owner;
    companiesService = {
      updateCompany: vi.fn().mockImplementation((id: number, body: object) => of({ id, ...body })),
    };
    companyState = {
      companies: signal([company]),
      notifyCompanyUpdated: vi.fn(),
      notifyCompanyDeleted: vi.fn(),
    };
    dialogResult = new Subject();
    dialog = { open: vi.fn().mockReturnValue({ afterClosed: () => dialogResult }) };
    snackBar = { open: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [
            { path: 'companies/:companyId/settings', component: CompanySettings },
            { path: 'companies', children: [] },
          ],
          withComponentInputBinding(),
        ),
        provideNativeDateAdapter(),
        { provide: CompaniesService, useValue: companiesService },
        { provide: CompanyStateService, useValue: companyState },
        {
          provide: CompanyRoleService,
          useValue: { getRole: () => Promise.resolve(role), invalidate: vi.fn() },
        },
        { provide: MatDialog, useValue: dialog },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
  });

  async function render(url = '/companies/7/settings') {
    const harness = await RouterTestingHarness.create(url);
    await harness.fixture.whenStable();
    return {
      harness,
      el: harness.routeNativeElement as HTMLElement,
      component: harness.routeDebugElement!.componentInstance as CompanySettings,
    };
  }

  it('fills the form from the company, pristine', async () => {
    const { component } = await render();
    const value = component.form.getRawValue();
    expect(value.name).toBe('Acme');
    expect(value.foundingDate?.getFullYear()).toBe(2020);
    expect(value.foundingDate?.getDate()).toBe(15);
    expect(component.form.pristine).toBe(true);
    expect(component.hasPendingChanges()).toBe(false);
  });

  it('saves changes and updates the shared company list', async () => {
    const { component } = await render();
    component.form.controls.name.setValue(' Acme Ltd ');
    component.form.controls.websiteUrl.setValue('acme.io');
    component.form.markAsDirty();
    await component.save();
    expect(companiesService.updateCompany).toHaveBeenCalledWith(7, {
      name: 'Acme Ltd',
      foundingDate: '2020-03-15',
      websiteUrl: 'https://acme.io',
      description: 'We make things.',
    });
    expect(companyState.notifyCompanyUpdated).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'Acme Ltd' }),
    );
    expect(component.form.pristine).toBe(true);
  });

  it('flags a name that another company uses', async () => {
    companiesService.updateCompany.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409 })),
    );
    const { component } = await render();
    component.form.controls.name.setValue('Globex');
    component.form.markAsDirty();
    await component.save();
    expect(component.form.controls.name.hasError('nameExists')).toBe(true);
    expect(component.error()).toContain('already uses this name');
  });

  it('discards edits', async () => {
    const { component } = await render();
    component.form.controls.description.setValue('Changed');
    component.form.markAsDirty();
    component.discard();
    expect(component.form.controls.description.value).toBe('We make things.');
    expect(component.form.pristine).toBe(true);
  });

  it('is read-only for HR managers, without the danger zone', async () => {
    role = CompanyRole.HrManager;
    const { el, component } = await render();
    expect(component.form.disabled).toBe(true);
    expect(el.textContent).toContain('Only owners and co-owners can change these details');
    expect(el.querySelector('.danger-zone')).toBeNull();
    expect(el.textContent).not.toContain('Save changes');
  });

  it('lets co-owners edit but not delete', async () => {
    role = CompanyRole.CoOwner;
    const { el, component } = await render();
    expect(component.form.enabled).toBe(true);
    expect(el.querySelector('.danger-zone')).toBeNull();
  });

  it('deletes through the typed-confirmation dialog and leaves the page', async () => {
    const { el, harness } = await render();
    el.querySelector<HTMLButtonElement>('.danger-zone .delete-button')!.click();
    expect(dialog.open).toHaveBeenCalledWith(
      DeleteCompanyDialog,
      expect.objectContaining({ data: { company } }),
    );
    dialogResult.next(true);
    await harness.fixture.whenStable();
    expect(companyState.notifyCompanyDeleted).toHaveBeenCalledWith(7);
    expect(TestBed.inject(Router).url).toBe('/companies');
  });

  it('shows not found for a company the user is not in', async () => {
    const { el } = await render('/companies/99/settings');
    expect(el.textContent).toContain('Company not found');
  });
});
