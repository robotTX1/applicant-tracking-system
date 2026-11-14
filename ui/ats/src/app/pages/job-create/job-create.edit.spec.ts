import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';
import {
  CompanyEmailTemplateType,
  CompanyResponse,
  EmailTemplateResponse,
  EmailTemplatesService,
  JobListingResponse,
  JobListingStatus,
  JobListingsService,
} from '../../api';
import { CompanyStateService } from '../../services/company-state.service';
import { JobCreate } from './job-create';

const company = (id: number, name: string): CompanyResponse => ({
  id,
  name,
  foundingDate: '2020-01-01',
  websiteUrl: '',
  description: '',
});

const template = (id: number, type: CompanyEmailTemplateType): EmailTemplateResponse => ({
  id,
  companyId: 1,
  type,
  subject: `Template ${id}`,
  body: '',
  createdAt: '',
  updatedAt: '',
});

const job = (status: JobListingStatus, extra: Partial<JobListingResponse> = {}) =>
  ({
    id: 50,
    companyId: 1,
    company: company(1, 'Acme'),
    title: 'Backend Developer',
    location: 'Remote',
    description: 'Build APIs',
    aiReviewEnabled: false,
    offerEmailTemplateId: 202, // not the first offer template
    declineEmailTemplateId: 102,
    applicationCount: 4,
    status,
    createdAt: '',
    updatedAt: '',
    ...extra,
  }) as JobListingResponse;

describe('JobCreate (edit mode)', () => {
  let fixture: ComponentFixture<JobCreate>;
  let component: JobCreate;
  let jobListingsService: {
    getJobListing: ReturnType<typeof vi.fn>;
    updateJobListing: ReturnType<typeof vi.fn>;
    saveJobListing: ReturnType<typeof vi.fn>;
  };
  let router: Router;
  let snackBar: MatSnackBar;

  async function setup(
    loaded: JobListingResponse | Error,
    companyId = '1',
    managerIds: ReadonlySet<number> = new Set([1, 2]),
  ) {
    jobListingsService = {
      getJobListing: vi
        .fn()
        .mockReturnValue(loaded instanceof Error ? throwError(() => loaded) : of(loaded)),
      updateJobListing: vi
        .fn()
        .mockImplementation((_id: number, req: object) =>
          of({ ...(loaded as JobListingResponse), ...req }),
        ),
      saveJobListing: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [JobCreate],
      providers: [
        provideRouter([]),
        { provide: JobListingsService, useValue: jobListingsService },
        {
          provide: EmailTemplatesService,
          useValue: {
            getEmailTemplates: vi.fn().mockReturnValue(
              of({
                content: [
                  template(101, CompanyEmailTemplateType.Offer),
                  template(202, CompanyEmailTemplateType.Offer),
                  template(102, CompanyEmailTemplateType.Decline),
                ],
                page: { size: 20, number: 0, totalElements: 3, totalPages: 1 },
              }),
            ),
          },
        },
        {
          provide: CompanyStateService,
          useValue: {
            init: vi.fn().mockResolvedValue(undefined),
            companies: signal([company(2, 'Beta'), company(1, 'Acme')]),
            jobManagerCompanies: signal(
              managerIds.has(2) ? [company(2, 'Beta'), company(1, 'Acme')] : [company(1, 'Acme')],
            ),
            jobManagerCompanyIds: signal(managerIds),
            hasCompany: signal(true),
          },
        },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(JobCreate);
    component = fixture.componentInstance;
    // The component's MatSnackBarModule import gives it its own instance
    snackBar = (component as unknown as { snackBar: MatSnackBar }).snackBar;
    vi.spyOn(snackBar, 'open');
    fixture.componentRef.setInput('companyId', companyId);
    fixture.componentRef.setInput('jobId', '50');
    await fixture.whenStable();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const button = (text: string) =>
    [...el().querySelectorAll<HTMLButtonElement>('button')].find((b) =>
      b.textContent?.includes(text),
    );

  it('loads the job and fills the form, keeping its company and templates', async () => {
    await setup(job(JobListingStatus.Published));
    expect(jobListingsService.getJobListing).toHaveBeenCalledWith(50);
    expect(component.form.getRawValue()).toEqual(
      expect.objectContaining({
        companyId: 1,
        title: 'Backend Developer',
        location: 'Remote',
        description: 'Build APIs',
        aiReviewEnabled: false,
        offerEmailTemplateId: 202,
        declineEmailTemplateId: 102,
      }),
    );
    expect(component.form.controls.aiReviewMinScore.disabled).toBe(true);
    expect(component.form.pristine).toBe(true);
    expect(component.hasPendingChanges()).toBe(false);
    expect(el().textContent).toContain('Edit Job Listing');
    expect(el().textContent).toContain('This job is live');
    expect(el().querySelector('mat-select[formControlName="companyId"]')).toBeNull();
  });

  it('saves changes of a published job with its status unchanged', async () => {
    await setup(job(JobListingStatus.Published));
    expect(button('Save Changes')?.disabled).toBe(true); // nothing changed yet
    component.form.controls.title.setValue('  Senior Backend Developer ');
    component.form.markAsDirty();
    fixture.detectChanges();
    expect(button('Save Changes')?.disabled).toBe(false);

    await component.saveChanges();

    expect(jobListingsService.updateJobListing).toHaveBeenCalledWith(50, {
      title: 'Senior Backend Developer',
      location: 'Remote',
      description: 'Build APIs',
      aiReviewEnabled: false,
      aiReviewMinScore: 70,
      offerEmailTemplateId: 202,
      declineEmailTemplateId: 102,
      status: JobListingStatus.Published,
    });
    expect(jobListingsService.saveJobListing).not.toHaveBeenCalled();
    expect(snackBar.open).toHaveBeenCalledWith(
      'Job listing "Senior Backend Developer" saved successfully!',
      'Close',
      expect.any(Object),
    );
    expect(router.navigate).toHaveBeenCalledWith(['/companies', 1, 'jobs']);
  });

  it('offers Save Draft and Save & Publish for drafts', async () => {
    await setup(job(JobListingStatus.Draft, { aiReviewEnabled: true, aiReviewMinScore: 85 }));
    expect(component.form.controls.aiReviewMinScore.value).toBe(85);
    expect(button('Save Draft')).toBeDefined();
    expect(button('Save & Publish')).toBeDefined();

    await component.publish();
    expect(jobListingsService.updateJobListing).toHaveBeenCalledWith(
      50,
      expect.objectContaining({ status: JobListingStatus.Published, aiReviewMinScore: 85 }),
    );
    expect(snackBar.open).toHaveBeenCalledWith(
      'Job listing "Backend Developer" saved and published successfully!',
      'Close',
      expect.any(Object),
    );
  });

  it('shows the server error and stays on the page when saving fails', async () => {
    await setup(job(JobListingStatus.Closed));
    jobListingsService.updateJobListing.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 400, error: { details: 'Bad template' } })),
    );
    await component.saveChanges();
    expect(component.serverError()).toBe('Bad template');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('shows not found when the job fails to load', async () => {
    await setup(new HttpErrorResponse({ status: 404 }));
    expect(el().textContent).toContain('Job listing not found');
    expect(el().querySelector('form')).toBeNull();
  });

  it('shows not found when the job belongs to another company than the URL', async () => {
    await setup(job(JobListingStatus.Draft), '2');
    expect(el().textContent).toContain('Job listing not found');
  });

  it('cancel goes back to the company job listings', async () => {
    await setup(job(JobListingStatus.Draft));
    component.cancel();
    expect(router.navigate).toHaveBeenCalledWith(['/companies', '1', 'jobs']);
  });

  it('shows not found for a job of a company where the user cannot manage jobs', async () => {
    await setup(job(JobListingStatus.Draft), '1', new Set([2]));
    expect(el().textContent).toContain('Job listing not found');
    expect(el().querySelector('form')).toBeNull();
  });
});
