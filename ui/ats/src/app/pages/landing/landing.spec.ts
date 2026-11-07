import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import Keycloak from 'keycloak-js';
import { CompanyStateService } from '../../services/company-state.service';
import { Landing, LANDING_TITLE } from './landing';

describe('Landing', () => {
  let keycloak: {
    authenticated: boolean;
    login: ReturnType<typeof vi.fn>;
    register: ReturnType<typeof vi.fn>;
  };
  let companyState: {
    init: ReturnType<typeof vi.fn>;
    canPostJobs: ReturnType<typeof signal<boolean>>;
    hasCompany: ReturnType<typeof signal<boolean>>;
    jobListingsLink: ReturnType<typeof signal<(string | number)[]>>;
  };

  beforeEach(() => {
    keycloak = { authenticated: false, login: vi.fn(), register: vi.fn() };
    companyState = {
      init: vi.fn().mockResolvedValue(undefined),
      canPostJobs: signal(false),
      hasCompany: signal(false),
      jobListingsLink: signal<(string | number)[]>(['/companies', 7, 'jobs']),
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: '', component: Landing },
          { path: 'jobs', children: [] },
        ]),
        { provide: Keycloak, useValue: keycloak },
        { provide: CompanyStateService, useValue: companyState },
      ],
    });
  });

  async function render() {
    const harness = await RouterTestingHarness.create('/');
    await harness.fixture.whenStable();
    return {
      harness,
      el: harness.routeNativeElement as HTMLElement,
      component: harness.routeDebugElement!.componentInstance as Landing,
    };
  }

  const sides = (el: HTMLElement) => [...el.querySelectorAll<HTMLElement>('#sides .side')];
  const companySide = (el: HTMLElement) => sides(el)[1];
  const button = (root: HTMLElement, text: string) =>
    [...root.querySelectorAll<HTMLElement>('a, button')].find((b) => b.textContent?.includes(text));

  it('lays the page out as hero, both sides, the product window and the closing footer', async () => {
    const { el } = await render();
    expect(el.querySelector('h1')?.textContent).toBe('Job board with AI CV screening');
    expect(el.querySelector('.hero__lead')?.textContent).toContain('scored from 0 to 100');
    expect(el.querySelector('app-hero-collage')).not.toBeNull();
    expect(sides(el).length).toBe(2);
    expect(el.querySelector('#product app-product-window')).not.toBeNull();
    expect(el.querySelector('footer.closing h2')?.textContent).toContain('Open jobs');
  });

  it('uses one wording for the main action, in the hero and the close', async () => {
    const { el } = await render();
    const mainActions = [...el.querySelectorAll('a')].filter(
      (a) => a.textContent?.trim() === 'Find a job',
    );
    expect(mainActions.map((a) => a.getAttribute('href'))).toEqual(['/jobs', '/jobs']);
  });

  it('keeps the revealed section readable where the reveal does not run', async () => {
    // jsdom has no matchMedia, so the reveal stays off like on a phone or under reduced motion
    const { el } = await render();
    const section = el.querySelector<HTMLElement>('#sides')!;
    expect(section.querySelector('.reveal')?.getAttribute('aria-hidden')).toBe('true');
    expect(section.classList).not.toContain('is-revealing');
    expect(section.querySelector<HTMLElement>('h2')!.style.opacity).toBe('');
  });

  it('sends signed-out visitors to sign-up for applying or for their company', async () => {
    const { el } = await render();
    expect(companyState.init).not.toHaveBeenCalled();
    button(sides(el)[0], 'Sign up to apply')!.click();
    expect(keycloak.register).toHaveBeenCalledWith({
      redirectUri: `${window.location.origin}/jobs`,
    });
    button(companySide(el), 'Sign up your company')!.click();
    expect(keycloak.register).toHaveBeenLastCalledWith({
      redirectUri: `${window.location.origin}/company/register`,
    });
    button(companySide(el), 'Sign in')!.click();
    expect(keycloak.login).toHaveBeenCalledWith({
      redirectUri: `${window.location.origin}/company/register`,
    });
  });

  it('links signed-in users to their applications, and to registration without a company', async () => {
    keycloak.authenticated = true;
    const { el, component } = await render();
    expect(companyState.init).toHaveBeenCalled();
    expect(button(sides(el)[0], 'See your applications')?.getAttribute('href')).toBe(
      '/my-applications',
    );
    expect(component.companyMode()).toBe('register');
    expect(button(companySide(el), 'Add your company')?.getAttribute('href')).toBe(
      '/company/register',
    );
  });

  it('offers adding a job listing to people who can post', async () => {
    keycloak.authenticated = true;
    companyState.hasCompany.set(true);
    companyState.canPostJobs.set(true);
    const { el } = await render();
    expect(button(companySide(el), 'Add a job listing')?.getAttribute('href')).toBe('/jobs/new');
    expect(button(companySide(el), 'Your job listings')?.getAttribute('href')).toBe(
      '/companies/7/jobs',
    );
  });

  it('points members without posting rights to their companies', async () => {
    keycloak.authenticated = true;
    companyState.hasCompany.set(true);
    const { el, component } = await render();
    expect(component.companyMode()).toBe('member');
    expect(button(companySide(el), 'See your companies')?.getAttribute('href')).toBe('/companies');
    expect(button(companySide(el), 'Add a job listing')).toBeUndefined();
  });

  it('sets the page title and restores it when leaving', async () => {
    const title = TestBed.inject(Title);
    title.setTitle('Ats');
    const { harness } = await render();
    expect(title.getTitle()).toBe(LANDING_TITLE);
    await harness.navigateByUrl('/jobs');
    expect(title.getTitle()).toBe('Ats');
  });
});
