import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { CompanyStateService } from './company-state.service';
import Keycloak from 'keycloak-js';
import { CompaniesService, CompanyResponse, CompanyRole } from '../api';
import { of, throwError } from 'rxjs';
import { ResourceLimit } from '../enums/resource-limit.enum';

@Component({ template: '' })
class Blank {}

describe('CompanyStateService', () => {
  let service: CompanyStateService;
  let mockKeycloak: Partial<Keycloak>;
  let mockCompaniesService: { getCompanies: ReturnType<typeof vi.fn> };

  const mockCompany: CompanyResponse = {
    id: 1,
    name: 'Acme Corp',
    foundingDate: '2020-01-01',
    websiteUrl: 'https://acme.com',
    description: 'Leading provider of tech solutions',
  };

  beforeEach(() => {
    mockKeycloak = {
      authenticated: true,
    };

    mockCompaniesService = {
      getCompanies: vi.fn().mockImplementation((_search, _isEmployee, role) => {
        if (role === CompanyRole.Owner) {
          return of({
            content: [mockCompany],
            page: { totalElements: 1, totalPages: 1, number: 0, size: 10 },
          });
        }
        return of({
          content: [mockCompany],
          page: { totalElements: 1, totalPages: 1, number: 0, size: 10 },
        });
      }),
    };

    TestBed.configureTestingModule({
      providers: [
        CompanyStateService,
        provideRouter([{ path: '**', component: Blank }]),
        { provide: Keycloak, useValue: mockKeycloak },
        { provide: CompaniesService, useValue: mockCompaniesService },
      ],
    });

    service = TestBed.inject(CompanyStateService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should refresh company state when authenticated', async () => {
    await service.refresh();
    expect(service.hasCompany()).toBe(true);
    expect(service.companies().length).toBe(1);
    expect(service.ownedCompaniesCount()).toBe(1);
    expect(service.canCreateCompany()).toBe(true);
    expect(service.isInitialized()).toBe(true);
    expect(service.error()).toBeNull();
  });

  it('should report canCreateCompany as false when limit is reached', async () => {
    mockCompaniesService.getCompanies.mockImplementation((_search, _isEmployee, role) => {
      if (role === CompanyRole.Owner) {
        return of({
          content: [mockCompany, mockCompany, mockCompany],
          page: { totalElements: ResourceLimit.MAX_COMPANIES, totalPages: 1, number: 0, size: 10 },
        });
      }
      return of({
        content: [mockCompany],
        page: { totalElements: 1, totalPages: 1, number: 0, size: 10 },
      });
    });

    await service.refresh();
    expect(service.ownedCompaniesCount()).toBe(ResourceLimit.MAX_COMPANIES);
    expect(service.canCreateCompany()).toBe(false);
  });

  it('should reset state when unauthenticated', async () => {
    mockKeycloak.authenticated = false;
    await service.refresh();
    expect(service.hasCompany()).toBe(false);
    expect(service.companies().length).toBe(0);
    expect(service.ownedCompaniesCount()).toBe(0);
    expect(service.isInitialized()).toBe(false);
  });

  it('should handle API errors gracefully', async () => {
    mockCompaniesService.getCompanies.mockReturnValue(throwError(() => new Error('Network error')));
    await expect(service.refresh()).resolves.toBeUndefined();
    expect(service.error()).toBeTruthy();
  });

  it('should immediately update state when notifyCompanyCreated is called', () => {
    const newCompany: CompanyResponse = {
      id: 2,
      name: 'New Startup',
      foundingDate: '2023-01-01',
      websiteUrl: 'https://startup.com',
      description: 'AI-driven products',
    };

    service.notifyCompanyCreated(newCompany);
    expect(service.companies()).toContainEqual(newCompany);
    expect(service.hasCompany()).toBe(true);
    expect(service.ownedCompaniesCount()).toBe(1);
  });

  it('should immediately update state when notifyCompanyDeleted is called', () => {
    service.companies.set([mockCompany]);
    service.ownedCompaniesCount.set(1);

    service.notifyCompanyDeleted(mockCompany.id);
    expect(service.companies()).toEqual([]);
    expect(service.hasCompany()).toBe(false);
    expect(service.ownedCompaniesCount()).toBe(0);
  });

  it('should share in-flight promise and properly initialize concurrent callers', async () => {
    await Promise.all([service.init(), service.init()]);
    expect(service.hasCompany()).toBe(true);
    expect(service.companies().length).toBe(1);
    expect(mockCompaniesService.getCompanies).toHaveBeenCalledTimes(4); // all, owned, co-owned, HR managed

    // Subsequent init should be a no-op
    await service.init();
    expect(mockCompaniesService.getCompanies).toHaveBeenCalledTimes(4);
  });

  it('should not lock initialization when init is called while unauthenticated', async () => {
    mockKeycloak.authenticated = false;
    await service.init();
    expect(mockCompaniesService.getCompanies).not.toHaveBeenCalled();
    expect(service.companies().length).toBe(0);
    expect(service.isInitialized()).toBe(false);

    // Later when user becomes authenticated, init should successfully fetch
    mockKeycloak.authenticated = true;
    await service.init();
    expect(mockCompaniesService.getCompanies).toHaveBeenCalledTimes(4);
    expect(service.companies().length).toBe(1);
    expect(service.isInitialized()).toBe(true);
  });

  it('links company pages directly when the user has one company', async () => {
    await service.refresh();
    expect(service.companyLink('team')).toEqual(['/companies', 1, 'team']);
    expect(service.jobListingsLink()).toEqual(['/companies', 1, 'jobs']);
    service.companies.set([mockCompany, { ...mockCompany, id: 2 }]);
    expect(service.teamLink()).toEqual(['/companies']);
  });

  it('links company pages to the company being viewed', async () => {
    service.companies.set([mockCompany, { ...mockCompany, id: 2 }]);
    expect(service.linkCompanyId()).toBeNull();

    await TestBed.inject(Router).navigateByUrl('/companies/2/jobs/7');
    expect(service.viewedCompanyId()).toBe(2);
    expect(service.teamLink()).toEqual(['/companies', 2, 'team']);
  });

  it('ignores a company in the URL the user does not belong to', async () => {
    service.companies.set([mockCompany, { ...mockCompany, id: 2 }]);
    await TestBed.inject(Router).navigateByUrl('/companies/99/team');
    expect(service.viewedCompanyId()).toBeNull();
    expect(service.settingsLink()).toEqual(['/companies']);
  });

  it('tracks the companies where the user can post jobs', async () => {
    const acme = mockCompany;
    const globex = { ...mockCompany, id: 2, name: 'Globex' };
    const initech = { ...mockCompany, id: 3, name: 'Initech' };
    const page = (content: CompanyResponse[]) =>
      of({
        content,
        page: { totalElements: content.length, totalPages: 1, number: 0, size: 10 },
      });
    mockCompaniesService.getCompanies.mockImplementation((_s, _e, role) =>
      role === CompanyRole.Owner
        ? page([acme])
        : role === CompanyRole.CoOwner
          ? page([])
          : role === CompanyRole.HrManager
            ? page([globex])
            : page([acme, globex, initech]),
    );

    await service.refresh();

    expect([...service.jobManagerCompanyIds()]).toEqual([1, 2]);
    expect(service.jobManagerCompanies().map((c) => c.name)).toEqual(['Acme Corp', 'Globex']);
    expect(service.canPostJobs()).toBe(true);

    service.notifyCompanyDeleted(1);
    expect([...service.jobManagerCompanyIds()]).toEqual([2]);
  });

  it('cannot post jobs when only a technical interviewer', async () => {
    mockCompaniesService.getCompanies.mockImplementation((_s, _e, role) =>
      of({
        content: role ? [] : [mockCompany],
        page: { totalElements: role ? 0 : 1, totalPages: 1, number: 0, size: 10 },
      }),
    );
    await service.refresh();
    expect(service.hasCompany()).toBe(true);
    expect(service.canPostJobs()).toBe(false);
  });
});
