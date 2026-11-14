import { ComponentFixture, TestBed } from '@angular/core/testing';
import { JobCreate } from './job-create';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { computed, Signal, signal } from '@angular/core';
import {
  CompanyEmailTemplateType,
  CompanyResponse,
  EmailTemplateResponse,
  EmailTemplatesService,
  JobListingResponse,
  JobListingStatus,
  JobListingsService,
  PagedEmailTemplateResponse,
} from '../../api';
import { CompanyStateService } from '../../services/company-state.service';
import { TemplatePreviewDialog } from '../../components/template-preview-dialog/template-preview-dialog';

describe('JobCreate', () => {
  let component: JobCreate;
  let fixture: ComponentFixture<JobCreate>;
  let mockJobListingsService: { saveJobListing: ReturnType<typeof vi.fn> };
  let mockEmailTemplatesService: { getEmailTemplates: ReturnType<typeof vi.fn> };
  let mockCompanyStateService: {
    init: ReturnType<typeof vi.fn>;
    companies: ReturnType<typeof signal<CompanyResponse[]>>;
    jobManagerCompanies: Signal<CompanyResponse[]>;
    jobManagerCompanyIds: Signal<ReadonlySet<number>>;
    hasCompany: Signal<boolean>;
  };
  let companiesSignal = signal<CompanyResponse[]>([]);
  let snackBar: MatSnackBar;
  let dialog: MatDialog;
  let router: Router;

  const mockCompany1: CompanyResponse = {
    id: 1,
    name: 'Acme Corp',
    foundingDate: '2020-01-01',
    websiteUrl: 'https://acme.com',
    description: 'An acme company',
  };

  const mockCompany2: CompanyResponse = {
    id: 2,
    name: 'Beta Global',
    foundingDate: '2019-03-10',
    websiteUrl: 'https://beta.com',
    description: 'A global tech company',
  };

  const mockOfferTemplate: EmailTemplateResponse = {
    id: 101,
    companyId: 1,
    type: CompanyEmailTemplateType.Offer,
    subject: 'Offer Letter: {{job_title}}',
    body: 'Congratulations {{candidate_name}}!',
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  };

  const mockDeclineTemplate: EmailTemplateResponse = {
    id: 102,
    companyId: 1,
    type: CompanyEmailTemplateType.Decline,
    subject: 'Application Status: {{job_title}}',
    body: 'Thank you for interviewing, {{candidate_name}}.',
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  };

  const mockPagedTemplates: PagedEmailTemplateResponse = {
    content: [mockOfferTemplate, mockDeclineTemplate],
    page: {
      size: 20,
      number: 0,
      totalElements: 2,
      totalPages: 1,
    },
  };

  const mockSavedJob: JobListingResponse = {
    id: 50,
    companyId: 1,
    company: {
      id: 1,
      name: 'Acme Corp',
      foundingDate: '2020-01-15',
      websiteUrl: 'https://acme.com',
      description: 'An acme company',
    },
    title: 'Senior Software Engineer',
    location: 'Budapest, Hungary',
    description: 'Exciting full-stack development position.',
    aiReviewEnabled: true,
    aiReviewMinScore: 70,
    offerEmailTemplateId: 101,
    declineEmailTemplateId: 102,
    status: JobListingStatus.Published,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  };

  beforeEach(async () => {
    companiesSignal = signal<CompanyResponse[]>([mockCompany1, mockCompany2]);

    mockJobListingsService = {
      saveJobListing: vi.fn().mockReturnValue(of(mockSavedJob)),
    };

    mockEmailTemplatesService = {
      getEmailTemplates: vi.fn().mockReturnValue(of(mockPagedTemplates)),
    };

    // The user can post jobs in every company they belong to
    mockCompanyStateService = {
      init: vi.fn().mockResolvedValue(undefined),
      companies: companiesSignal,
      jobManagerCompanies: computed(() => companiesSignal()),
      jobManagerCompanyIds: computed(() => new Set(companiesSignal().map((c) => c.id))),
      hasCompany: computed(() => companiesSignal().length > 0),
    };

    await TestBed.configureTestingModule({
      imports: [JobCreate],
      providers: [
        provideRouter([]),
        { provide: JobListingsService, useValue: mockJobListingsService },
        { provide: EmailTemplatesService, useValue: mockEmailTemplatesService },
        { provide: CompanyStateService, useValue: mockCompanyStateService },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap({ companyId: '2' }),
            },
            queryParamMap: of(convertToParamMap({ companyId: '2' })),
          },
        },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture = TestBed.createComponent(JobCreate);
    component = fixture.componentInstance;
    snackBar = (component as any).snackBar;
    dialog = (component as any).dialog;
    vi.spyOn(snackBar, 'open');
    vi.spyOn(dialog, 'open').mockReturnValue({} as any);

    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create and preselect company from query parameters', () => {
    expect(component).toBeTruthy();
    expect(component.form.controls.companyId.value).toBe(2);
    expect(mockEmailTemplatesService.getEmailTemplates).toHaveBeenCalledWith(2);
  });

  it('should auto-populate offer and decline templates when loaded', () => {
    expect(component.offerTemplates().length).toBe(1);
    expect(component.declineTemplates().length).toBe(1);
    expect(component.form.controls.offerEmailTemplateId.value).toBe(101);
    expect(component.form.controls.declineEmailTemplateId.value).toBe(102);
  });

  it('should open template preview dialog with correct data', () => {
    component.openTemplatePreview(101);
    expect(dialog.open).toHaveBeenCalledWith(
      TemplatePreviewDialog,
      expect.objectContaining({
        data: expect.objectContaining({
          type: CompanyEmailTemplateType.Offer,
          subject: 'Offer Letter: {{job_title}}',
        }),
      }),
    );
  });

  it('should adjust validator and disabled state when aiReviewEnabled is toggled', () => {
    expect(component.form.controls.aiReviewMinScore.validator).toBeTruthy();
    expect(component.form.controls.aiReviewMinScore.disabled).toBe(false);

    component.form.controls.aiReviewEnabled.setValue(false);
    expect(component.isAiReviewActive()).toBe(false);
    expect(component.form.controls.aiReviewMinScore.validator).toBeNull();
    expect(component.form.controls.aiReviewMinScore.disabled).toBe(true);

    component.form.controls.aiReviewEnabled.setValue(true);
    expect(component.isAiReviewActive()).toBe(true);
    expect(component.form.controls.aiReviewMinScore.validator).toBeTruthy();
    expect(component.form.controls.aiReviewMinScore.disabled).toBe(false);
  });

  it('should compute score tiers accurately based on threshold value', () => {
    expect(component.getScoreTier(85).tierClass).toBe('tier-strict');
    expect(component.getScoreTier(70).tierClass).toBe('tier-balanced');
    expect(component.getScoreTier(50).tierClass).toBe('tier-lenient');
  });

  it('should publish listing successfully and redirect to /companies', async () => {
    component.form.setValue({
      companyId: 1,
      title: 'Senior Software Engineer',
      location: 'Budapest, Hungary',
      description: 'Looking for a seasoned backend engineer with Spring experience.',
      aiReviewEnabled: true,
      aiReviewMinScore: 70,
      offerEmailTemplateId: 101,
      declineEmailTemplateId: 102,
    });

    expect(component.form.valid).toBe(true);

    await component.publish();

    expect(mockJobListingsService.saveJobListing).toHaveBeenCalledWith({
      companyId: 1,
      title: 'Senior Software Engineer',
      location: 'Budapest, Hungary',
      description: 'Looking for a seasoned backend engineer with Spring experience.',
      aiReviewEnabled: true,
      aiReviewMinScore: 70,
      offerEmailTemplateId: 101,
      declineEmailTemplateId: 102,
      status: JobListingStatus.Published,
    });

    expect(snackBar.open).toHaveBeenCalledWith(
      'Job listing "Senior Software Engineer" published successfully!',
      'Close',
      expect.any(Object),
    );
    expect(router.navigate).toHaveBeenCalledWith(['/companies', 1, 'jobs']);
  });

  it('should save as draft successfully with JobListingStatus.Draft', async () => {
    component.form.setValue({
      companyId: 1,
      title: 'Draft Position',
      location: 'Remote',
      description: 'Work in progress job description for internal review.',
      aiReviewEnabled: false,
      aiReviewMinScore: 70,
      offerEmailTemplateId: 101,
      declineEmailTemplateId: 102,
    });

    expect(component.form.valid).toBe(true);

    await component.saveDraft();

    expect(mockJobListingsService.saveJobListing).toHaveBeenCalledWith({
      companyId: 1,
      title: 'Draft Position',
      location: 'Remote',
      description: 'Work in progress job description for internal review.',
      aiReviewEnabled: false,
      aiReviewMinScore: 70,
      offerEmailTemplateId: 101,
      declineEmailTemplateId: 102,
      status: JobListingStatus.Draft,
    });

    expect(snackBar.open).toHaveBeenCalledWith(
      'Job listing "Senior Software Engineer" saved as draft successfully!',
      'Close',
      expect.any(Object),
    );
  });

  it('should show a friendly message instead of the raw HTTP error text', async () => {
    mockJobListingsService.saveJobListing.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500, url: 'https://api/job-listings' })),
    );
    component.form.setValue({
      companyId: 1,
      title: 'Role',
      location: 'Remote',
      description: 'Role description here.',
      aiReviewEnabled: true,
      aiReviewMinScore: 70,
      offerEmailTemplateId: 101,
      declineEmailTemplateId: 102,
    });

    await component.publish();

    expect(component.serverError()).toBe('An error occurred while saving the job listing.');
  });

  it('should handle backend errors on save', async () => {
    mockJobListingsService.saveJobListing.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: {
              title: 'Bad Request',
              details: 'Cannot create a job listing with CLOSED status',
            },
          }),
      ),
    );

    component.form.setValue({
      companyId: 1,
      title: 'Invalid Role',
      location: 'Remote',
      description: 'Role description here.',
      aiReviewEnabled: true,
      aiReviewMinScore: 70,
      offerEmailTemplateId: 101,
      declineEmailTemplateId: 102,
    });

    await component.publish();

    expect(component.serverError()).toBe('Cannot create a job listing with CLOSED status');
    expect(component.isSubmitting()).toBe(false);
  });

  it('should return pending changes only when dirty and not submitted', () => {
    expect(component.hasPendingChanges()).toBe(false);

    component.form.markAsDirty();
    expect(component.hasPendingChanges()).toBe(true);

    component.isSubmitting.set(true);
    expect(component.hasPendingChanges()).toBe(false);

    component.isSubmitting.set(false);
    component.isSubmitted.set(true);
    expect(component.hasPendingChanges()).toBe(false);
  });

  it('should re-fetch templates when company selection changes', async () => {
    mockEmailTemplatesService.getEmailTemplates.mockClear();

    const company1Templates: PagedEmailTemplateResponse = {
      content: [
        {
          id: 201,
          companyId: 1,
          type: CompanyEmailTemplateType.Offer,
          subject: 'Acme Offer Letter',
          body: 'Welcome to Acme!',
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
        },
        {
          id: 202,
          companyId: 1,
          type: CompanyEmailTemplateType.Decline,
          subject: 'Acme Rejection Notice',
          body: 'Thank you for your interest in Acme.',
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
        },
      ],
      page: { size: 20, number: 0, totalElements: 2, totalPages: 1 },
    };

    mockEmailTemplatesService.getEmailTemplates.mockReturnValue(of(company1Templates));

    component.form.controls.companyId.setValue(1);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(mockEmailTemplatesService.getEmailTemplates).toHaveBeenCalledWith(1);
    expect(component.form.controls.offerEmailTemplateId.value).toBe(201);
    expect(component.form.controls.declineEmailTemplateId.value).toBe(202);
  });

  it('should fallback to first company when no query parameter is provided', async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [JobCreate],
      providers: [
        provideRouter([]),
        { provide: JobListingsService, useValue: mockJobListingsService },
        { provide: EmailTemplatesService, useValue: mockEmailTemplatesService },
        { provide: CompanyStateService, useValue: mockCompanyStateService },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap({}),
            },
            queryParamMap: of(convertToParamMap({})),
          },
        },
      ],
    }).compileComponents();

    const noParamFixture = TestBed.createComponent(JobCreate);
    const noParamComponent = noParamFixture.componentInstance;
    noParamFixture.detectChanges();
    await noParamFixture.whenStable();

    expect(noParamComponent.form.controls.companyId.value).toBe(1);
    expect(mockEmailTemplatesService.getEmailTemplates).toHaveBeenCalledWith(1);
  });

  it('should support router component input binding for companyId', async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [JobCreate],
      providers: [
        provideRouter([]),
        { provide: JobListingsService, useValue: mockJobListingsService },
        { provide: EmailTemplatesService, useValue: mockEmailTemplatesService },
        { provide: CompanyStateService, useValue: mockCompanyStateService },
      ],
    }).compileComponents();

    const inputFixture = TestBed.createComponent(JobCreate);
    inputFixture.componentRef.setInput('companyId', '2');
    inputFixture.detectChanges();
    await inputFixture.whenStable();

    expect(inputFixture.componentInstance.form.controls.companyId.value).toBe(2);
    expect(mockEmailTemplatesService.getEmailTemplates).toHaveBeenCalledWith(2);
  });

  it('should resolve and select company when companyState initializes asynchronously', async () => {
    const emptySignal = signal<CompanyResponse[]>([]);
    mockCompanyStateService.companies = emptySignal;
    mockCompanyStateService.jobManagerCompanies = computed(() => emptySignal());
    mockCompanyStateService.jobManagerCompanyIds = computed(
      () => new Set(emptySignal().map((c) => c.id)),
    );
    mockCompanyStateService.hasCompany = computed(() => emptySignal().length > 0);
    mockCompanyStateService.init.mockImplementation(async () => {
      emptySignal.set([mockCompany1]);
    });

    const newFixture = TestBed.createComponent(JobCreate);
    const newComponent = newFixture.componentInstance;
    newFixture.detectChanges();
    await newFixture.whenStable();

    expect(newComponent.form.controls.companyId.value).toBe(1);
    expect(mockEmailTemplatesService.getEmailTemplates).toHaveBeenCalledWith(1);
  });

  it('should keep form pristine after initial default population so pendingChangesGuard passes', () => {
    expect(component.form.pristine).toBe(true);
    expect(component.form.dirty).toBe(false);
    expect(component.hasPendingChanges()).toBe(false);
  });

  it('explains that members without job rights cannot post jobs', async () => {
    const noRights = signal<CompanyResponse[]>([]);
    mockCompanyStateService.jobManagerCompanies = noRights;
    mockCompanyStateService.jobManagerCompanyIds = signal(new Set<number>());
    mockCompanyStateService.hasCompany = signal(true);

    const newFixture = TestBed.createComponent(JobCreate);
    newFixture.detectChanges();
    await newFixture.whenStable();

    const text = (newFixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain("You can't post jobs yet");
    expect((newFixture.nativeElement as HTMLElement).querySelector('form')).toBeNull();
  });

  it('explains a template loading failure instead of showing empty dropdowns', async () => {
    mockEmailTemplatesService.getEmailTemplates.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404 })),
    );
    const newFixture = TestBed.createComponent(JobCreate);
    newFixture.detectChanges();
    await newFixture.whenStable();
    newFixture.detectChanges();

    expect(newFixture.componentInstance.templateLoadError()).toContain('Could not load');
    expect(newFixture.componentInstance.availableTemplates()).toEqual([]);
  });
});
