import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CompanyRegister } from './company-register';
import { provideRouter, Router } from '@angular/router';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { CompaniesService, CompanyResponse } from '../../api';
import { CompanyStateService } from '../../services/company-state.service';
import { ResourceLimit } from '../../enums/resource-limit.enum';

describe('CompanyRegister', () => {
  let component: CompanyRegister;
  let fixture: ComponentFixture<CompanyRegister>;
  let mockCompaniesService: { saveCompany: ReturnType<typeof vi.fn> };
  let mockCompanyStateService: {
    init: ReturnType<typeof vi.fn>;
    notifyCompanyCreated: ReturnType<typeof vi.fn>;
    canCreateCompany: ReturnType<typeof signal<boolean>>;
    ownedCompaniesCount: ReturnType<typeof signal<number>>;
    companies: ReturnType<typeof signal<CompanyResponse[]>>;
  };
  let canCreateCompanySig = signal<boolean>(true);
  let ownedCompaniesCountSig = signal<number>(0);
  let snackBar: MatSnackBar;
  let router: Router;

  const mockCreatedCompany: CompanyResponse = {
    id: 10,
    name: 'Tech Innovations Ltd',
    foundingDate: '2021-05-15',
    websiteUrl: 'https://techinno.com',
    description: 'Cutting edge software solutions',
  };

  beforeEach(async () => {
    canCreateCompanySig = signal<boolean>(true);
    ownedCompaniesCountSig = signal<number>(0);

    mockCompaniesService = {
      saveCompany: vi.fn().mockReturnValue(of(mockCreatedCompany)),
    };

    mockCompanyStateService = {
      init: vi.fn().mockResolvedValue(undefined),
      notifyCompanyCreated: vi.fn(),
      canCreateCompany: canCreateCompanySig,
      ownedCompaniesCount: ownedCompaniesCountSig,
      companies: signal<CompanyResponse[]>([]),
    };

    await TestBed.configureTestingModule({
      imports: [CompanyRegister],
      providers: [
        provideRouter([]),
        provideNativeDateAdapter(),
        { provide: CompaniesService, useValue: mockCompaniesService },
        { provide: CompanyStateService, useValue: mockCompanyStateService },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture = TestBed.createComponent(CompanyRegister);
    component = fixture.componentInstance;
    snackBar = (component as any).snackBar;
    vi.spyOn(snackBar, 'open');
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
    expect(component.form.valid).toBe(false);
  });

  it('should validate required fields', () => {
    expect(component.form.valid).toBe(false);
    expect(component.form.controls.name.hasError('required')).toBe(true);
    expect(component.form.controls.foundingDate.hasError('required')).toBe(true);
    expect(component.form.controls.websiteUrl.hasError('required')).toBe(true);
    expect(component.form.controls.description.hasError('required')).toBe(true);
  });

  it('should auto-normalize website url on blur', () => {
    component.form.controls.websiteUrl.setValue('example.com');
    component.normalizeWebsite();
    expect(component.form.controls.websiteUrl.value).toBe('https://example.com');
  });

  it('should submit successfully and redirect to /jobs/new', async () => {
    component.form.setValue({
      name: 'Tech Innovations Ltd',
      foundingDate: new Date('2021-05-15'),
      websiteUrl: 'https://techinno.com',
      description: 'Cutting edge software solutions',
    });

    expect(component.form.valid).toBe(true);

    await component.onSubmit();

    expect(mockCompaniesService.saveCompany).toHaveBeenCalledWith({
      name: 'Tech Innovations Ltd',
      foundingDate: '2021-05-15',
      websiteUrl: 'https://techinno.com',
      description: 'Cutting edge software solutions',
    });
    expect(mockCompanyStateService.notifyCompanyCreated).toHaveBeenCalledWith(mockCreatedCompany);
    expect(snackBar.open).toHaveBeenCalledWith(
      expect.stringContaining('Tech Innovations Ltd'),
      'Close',
      expect.any(Object),
    );
    expect(router.navigate).toHaveBeenCalledWith(['/jobs/new']);
    expect(component.isSubmitted()).toBe(true);
  });

  it('should handle 409 conflict when company name exists', async () => {
    mockCompaniesService.saveCompany.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, statusText: 'Conflict' })),
    );

    component.form.setValue({
      name: 'Duplicate Corp',
      foundingDate: new Date('2020-01-01'),
      websiteUrl: 'https://duplicate.com',
      description: 'Existing company',
    });

    await component.onSubmit();

    expect(component.isSubmitting()).toBe(false);
    expect(component.serverError()).toContain('already exists');
    expect(component.form.controls.name.hasError('nameExists')).toBe(true);
  });

  it('should display limit reached card when user reached maximum companies', () => {
    canCreateCompanySig.set(false);
    ownedCompaniesCountSig.set(ResourceLimit.MAX_COMPANIES);
    fixture.detectChanges();

    expect(component.limitReached()).toBe(true);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.limit-card')).toBeTruthy();
    expect(compiled.textContent).toContain('Company Limit Reached');
  });

  it('should track pending changes accurately', () => {
    expect(component.hasPendingChanges()).toBe(false);

    component.form.controls.name.setValue('Changed Name');
    component.form.controls.name.markAsDirty();
    expect(component.hasPendingChanges()).toBe(true);

    component.isSubmitted.set(true);
    expect(component.hasPendingChanges()).toBe(false);
  });

  it('should navigate to /companies on cancel', () => {
    component.cancel();
    expect(router.navigate).toHaveBeenCalledWith(['/companies']);
  });
});
