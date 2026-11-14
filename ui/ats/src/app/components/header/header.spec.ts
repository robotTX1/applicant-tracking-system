import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Header } from './header';
import Keycloak from 'keycloak-js';
import { CompaniesService } from '../../api';
import { BreakpointObserver } from '@angular/cdk/layout';
import { of } from 'rxjs';

describe('Header', () => {
  let component: Header;
  let fixture: ComponentFixture<Header>;

  const mockKeycloak = {
    authenticated: false,
    tokenParsed: {
      name: 'Test User',
      preferred_username: 'testuser',
      email: 'test@example.com',
    },
    login: vi.fn(),
    logout: vi.fn(),
    register: vi.fn(),
  };

  const mockCompaniesService = {
    getCompanies: vi
      .fn()
      .mockReturnValue(
        of({ content: [], page: { totalElements: 0, totalPages: 0, number: 0, size: 10 } }),
      ),
  };

  const mockBreakpointObserver = {
    observe: vi.fn().mockReturnValue(of({ matches: false })),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Header],
      providers: [
        provideRouter([]),
        { provide: Keycloak, useValue: mockKeycloak },
        { provide: CompaniesService, useValue: mockCompaniesService },
        { provide: BreakpointObserver, useValue: mockBreakpointObserver },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Header);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('links the logo to the landing page for signed-out visitors', () => {
    const logo = (fixture.nativeElement as HTMLElement).querySelector('.brand');
    expect(logo?.getAttribute('href')).toBe('/');
  });

  it('links the logo to the job board for signed-in users', () => {
    mockKeycloak.authenticated = true;
    try {
      const signedIn = TestBed.createComponent(Header);
      signedIn.detectChanges();
      const logo = (signedIn.nativeElement as HTMLElement).querySelector('.brand');
      expect(logo?.getAttribute('href')).toBe('/jobs');
    } finally {
      mockKeycloak.authenticated = false;
    }
  });
});
