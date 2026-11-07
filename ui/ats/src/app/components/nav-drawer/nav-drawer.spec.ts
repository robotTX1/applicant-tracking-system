import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NavDrawer } from './nav-drawer';
import Keycloak from 'keycloak-js';
import { CompaniesService } from '../../api';
import { of } from 'rxjs';

describe('NavDrawer', () => {
  let component: NavDrawer;
  let fixture: ComponentFixture<NavDrawer>;

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

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NavDrawer],
      providers: [
        provideRouter([]),
        { provide: Keycloak, useValue: mockKeycloak },
        { provide: CompaniesService, useValue: mockCompaniesService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(NavDrawer);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('links the logo to the landing page for signed-out visitors', () => {
    const logo = (fixture.nativeElement as HTMLElement).querySelector('.drawer-brand');
    expect(logo?.getAttribute('href')).toBe('/');
  });

  it('closes from its close button', () => {
    const closed = vi.fn();
    component.navItemClicked.subscribe(closed);
    const close = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      'button[aria-label="Close menu"]',
    );
    close?.click();
    expect(closed).toHaveBeenCalledTimes(1);
  });

  it('offers sign-up and sign-in to signed-out visitors', () => {
    const footer = (fixture.nativeElement as HTMLElement).querySelector('.drawer-footer');
    expect(footer?.textContent).toContain('Sign up');
    expect(footer?.textContent).toContain('Sign in');
  });

  it('links the logo to the job board for signed-in users', () => {
    mockKeycloak.authenticated = true;
    try {
      const signedIn = TestBed.createComponent(NavDrawer);
      signedIn.detectChanges();
      const logo = (signedIn.nativeElement as HTMLElement).querySelector('.drawer-brand');
      expect(logo?.getAttribute('href')).toBe('/jobs');
    } finally {
      mockKeycloak.authenticated = false;
    }
  });
});
