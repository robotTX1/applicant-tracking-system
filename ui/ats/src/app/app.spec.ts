import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import Keycloak from 'keycloak-js';
import { CompaniesService } from './api';
import { of } from 'rxjs';

describe('App', () => {
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
      imports: [App],
      providers: [
        provideRouter([]),
        { provide: Keycloak, useValue: mockKeycloak },
        { provide: CompaniesService, useValue: mockCompaniesService },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });
});
