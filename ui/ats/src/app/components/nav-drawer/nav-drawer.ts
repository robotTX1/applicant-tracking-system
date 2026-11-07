import { Component, computed, inject, output } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import Keycloak from 'keycloak-js';
import { Logo } from '../logo/logo';
import { CompanyStateService } from '../../services/company-state.service';

@Component({
  selector: 'app-nav-drawer',
  imports: [
    RouterLink,
    RouterLinkActive,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatDividerModule,
    Logo,
  ],
  templateUrl: './nav-drawer.html',
  styleUrl: './nav-drawer.scss',
})
export class NavDrawer {
  protected readonly keycloak = inject(Keycloak);
  /** The logo opens the landing page for visitors and the job board for signed-in users. */
  protected readonly homeLink = this.keycloak.authenticated ? '/jobs' : '/';
  protected readonly companyState = inject(CompanyStateService);
  private readonly router = inject(Router);

  // Emitted whenever a link/action is clicked so the drawer closes
  readonly navItemClicked = output<void>();

  // Reactive company membership state
  protected readonly hasCompany = this.companyState.hasCompany;
  protected readonly canCreateCompany = this.companyState.canCreateCompany;

  /**
   * With several companies and none open, the company links all lead to the company list: then
   * only "My companies" shows as the current page.
   */
  protected readonly companyLinksLeadToCompany = computed(
    () => this.companyState.linkCompanyId() !== null,
  );

  constructor() {
    if (this.keycloak.authenticated && this.companyState.companies().length === 0) {
      this.companyState.init().catch((err: unknown) => {
        console.error('Failed to initialize company state in nav drawer:', err);
      });
    }
  }

  onLinkClick(): void {
    this.navItemClicked.emit();
  }

  onLogin(): void {
    this.navItemClicked.emit();
    this.keycloak.login();
  }

  onRegister(): void {
    this.navItemClicked.emit();
    this.keycloak.register();
  }

  onRegisterCompany(): void {
    this.navItemClicked.emit();
    if (!this.keycloak.authenticated) {
      this.keycloak.login({
        redirectUri: window.location.origin + '/company/register',
      });
    } else {
      this.router.navigate(['/company/register']);
    }
  }
}
