import { Component, inject, output } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { BreakpointObserver } from '@angular/cdk/layout';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import Keycloak from 'keycloak-js';
import { Logo } from '../logo/logo';
import { ThemeService } from '../../services/theme.service';
import { CompanyStateService } from '../../services/company-state.service';

@Component({
  selector: 'app-header',
  imports: [
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatDividerModule,
    Logo,
  ],
  templateUrl: './header.html',
  styleUrl: './header.scss',
})
export class Header {
  protected readonly keycloak = inject(Keycloak);
  /** The logo opens the landing page for visitors and the job board for signed-in users. */
  protected readonly homeLink = this.keycloak.authenticated ? '/jobs' : '/';
  protected readonly themeService = inject(ThemeService);
  protected readonly companyState = inject(CompanyStateService);
  private readonly router = inject(Router);
  private readonly breakpointObserver = inject(BreakpointObserver);

  // Emits event to toggle mobile side drawer
  readonly menuToggle = output<void>();

  // Responsive signal: true when viewport <= 1100px (the full recruiter header needs ~1050px)
  protected readonly isMobile = toSignal(
    this.breakpointObserver.observe('(max-width: 1100px)').pipe(map((result) => result.matches)),
    { initialValue: false },
  );

  // Reactive company membership state
  protected readonly hasCompany = this.companyState.hasCompany;
  protected readonly canCreateCompany = this.companyState.canCreateCompany;

  constructor() {
    if (this.keycloak.authenticated && this.companyState.companies().length === 0) {
      this.companyState.init().catch((err: unknown) => {
        console.error('Failed to initialize company state in header:', err);
      });
    }
  }

  get username(): string {
    return (
      this.keycloak.tokenParsed?.['name'] ||
      this.keycloak.tokenParsed?.['preferred_username'] ||
      'User'
    );
  }

  get userEmail(): string {
    return this.keycloak.tokenParsed?.['email'] || '';
  }

  login(): void {
    this.keycloak.login();
  }

  register(): void {
    this.keycloak.register();
  }

  logout(): void {
    this.keycloak.logout({ redirectUri: window.location.origin });
  }

  registerCompany(): void {
    if (!this.keycloak.authenticated) {
      this.keycloak.login({
        redirectUri: window.location.origin + '/company/register',
      });
    } else {
      this.router.navigate(['/company/register']);
    }
  }
}
