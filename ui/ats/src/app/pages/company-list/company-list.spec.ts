import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CompanyList } from './company-list';
import { provideRouter } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { signal } from '@angular/core';
import { CompaniesService, CompanyResponse } from '../../api';
import { CompanyStateService } from '../../services/company-state.service';
import { ResourceLimit } from '../../enums/resource-limit.enum';
import { DeleteCompanyDialog } from '../../components/delete-company-dialog/delete-company-dialog';

describe('CompanyList', () => {
  let component: CompanyList;
  let fixture: ComponentFixture<CompanyList>;
  let mockCompaniesService: { deleteCompany: ReturnType<typeof vi.fn> };
  let mockCompanyStateService: {
    init: ReturnType<typeof vi.fn>;
    notifyCompanyDeleted: ReturnType<typeof vi.fn>;
    companies: ReturnType<typeof signal<CompanyResponse[]>>;
    canCreateCompany: ReturnType<typeof signal<boolean>>;
    ownedCompaniesCount: ReturnType<typeof signal<number>>;
    jobManagerCompanyIds: ReturnType<typeof signal<ReadonlySet<number>>>;
  };
  let mockDialog: { open: ReturnType<typeof vi.fn> };
  let mockSnackBar: { open: ReturnType<typeof vi.fn> };

  const companiesSig = signal<CompanyResponse[]>([]);
  const canCreateCompanySig = signal<boolean>(true);
  const ownedCompaniesCountSig = signal<number>(0);
  const jobManagerIdsSig = signal<ReadonlySet<number>>(new Set());

  const sampleCompanies: CompanyResponse[] = [
    {
      id: 1,
      name: 'Acme Corp',
      foundingDate: '2020-01-15',
      websiteUrl: 'https://acme.com',
      description: 'Acme software solutions',
    },
    {
      id: 2,
      name: 'Globex Ltd',
      foundingDate: '2022-03-10',
      websiteUrl: 'https://globex.com',
      description: 'Globex technologies',
    },
  ];

  beforeEach(async () => {
    companiesSig.set([]);
    canCreateCompanySig.set(true);
    ownedCompaniesCountSig.set(0);
    jobManagerIdsSig.set(new Set());

    mockCompaniesService = {
      deleteCompany: vi.fn().mockReturnValue(of(void 0)),
    };

    mockCompanyStateService = {
      init: vi.fn().mockResolvedValue(undefined),
      notifyCompanyDeleted: vi.fn(),
      companies: companiesSig,
      canCreateCompany: canCreateCompanySig,
      ownedCompaniesCount: ownedCompaniesCountSig,
      jobManagerCompanyIds: jobManagerIdsSig,
    };

    mockDialog = {
      open: vi.fn(),
    };

    mockSnackBar = {
      open: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [CompanyList],
      providers: [
        provideRouter([]),
        { provide: CompaniesService, useValue: mockCompaniesService },
        { provide: CompanyStateService, useValue: mockCompanyStateService },
        { provide: MatDialog, useValue: mockDialog },
        { provide: MatSnackBar, useValue: mockSnackBar },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CompanyList);
    component = fixture.componentInstance;
    vi.spyOn((component as any).snackBar, 'open');
    fixture.detectChanges();
  });

  it('should create and call companyState.init()', () => {
    expect(component).toBeTruthy();
    expect(mockCompanyStateService.init).toHaveBeenCalled();
  });

  it('should display empty state when companies list is empty', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.empty-state-card')).toBeTruthy();
    expect(el.textContent).toContain('Start by Registering Your Company');
    expect(el.querySelectorAll('.company-card').length).toBe(0);
  });

  it('should render company cards when companies exist', () => {
    companiesSig.set(sampleCompanies);
    ownedCompaniesCountSig.set(2);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.empty-state-card')).toBeNull();
    const cards = el.querySelectorAll('.company-card');
    expect(cards.length).toBe(2);
    expect(el.textContent).toContain('Acme Corp');
    expect(el.textContent).toContain('Globex Ltd');
    expect(el.textContent).toContain('2 / 3 Used');
  });

  it('shows Post Job only on companies where the user can post jobs', () => {
    companiesSig.set(sampleCompanies);
    jobManagerIdsSig.set(new Set([sampleCompanies[0].id]));
    fixture.detectChanges();

    const [manageable, readOnly] = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.company-card'),
    );
    expect(manageable.textContent).toContain('Post Job');
    expect(readOnly.textContent).not.toContain('Post Job');
  });

  it('should compute initial letter correctly', () => {
    expect(component.getCompanyInitial('Acme Corp')).toBe('A');
    expect(component.getCompanyInitial('  globex')).toBe('G');
    expect(component.getCompanyInitial('')).toBe('C');
  });

  it('should disable register button and highlight limit when limit is reached', () => {
    canCreateCompanySig.set(false);
    ownedCompaniesCountSig.set(ResourceLimit.MAX_COMPANIES);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const registerBtn = el.querySelector('.header-actions button[disabled]');
    expect(registerBtn).toBeTruthy();
    const limitPill = el.querySelector('.limit-pill.limit-full');
    expect(limitPill).toBeTruthy();
    expect(limitPill?.textContent).toContain('3 / 3 Used');
  });

  it('opens the typed-confirmation dialog and updates the list once deleted', () => {
    mockDialog.open.mockReturnValue({ afterClosed: () => of(true) });

    component.deleteCompany(sampleCompanies[0]);

    expect(mockDialog.open).toHaveBeenCalledWith(
      DeleteCompanyDialog,
      expect.objectContaining({ data: { company: sampleCompanies[0] } }),
    );
    expect(mockCompanyStateService.notifyCompanyDeleted).toHaveBeenCalledWith(
      sampleCompanies[0].id,
    );
    expect((component as any).snackBar.open).toHaveBeenCalledWith(
      expect.stringContaining('Acme Corp'),
      'Close',
      expect.any(Object),
    );
  });

  it('does nothing when the delete dialog is cancelled', () => {
    mockDialog.open.mockReturnValue({ afterClosed: () => of(false) });

    component.deleteCompany(sampleCompanies[0]);

    expect(mockCompanyStateService.notifyCompanyDeleted).not.toHaveBeenCalled();
  });
});
