import { computed, effect, inject, Injectable, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import Keycloak from 'keycloak-js';
import { KEYCLOAK_EVENT_SIGNAL, KeycloakEventType } from 'keycloak-angular';
import { filter, firstValueFrom, map } from 'rxjs';
import { CompaniesService, CompanyResponse, CompanyRole } from '../api';
import { ResourceLimit } from '../enums/resource-limit.enum';

@Injectable({
  providedIn: 'root',
})
export class CompanyStateService {
  private readonly keycloak = inject(Keycloak);
  private readonly keycloakSignal = inject(KEYCLOAK_EVENT_SIGNAL, { optional: true });
  private readonly companiesService = inject(CompaniesService);
  private readonly router = inject(Router);

  /** The URL after each navigation, to know which company's pages are open. */
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  readonly companies = signal<CompanyResponse[]>([]);
  readonly ownedCompaniesCount = signal<number>(0);
  /** Companies where the user can post and manage jobs (owner, co-owner or HR manager). */
  readonly jobManagerCompanyIds = signal<ReadonlySet<number>>(new Set());
  readonly isInitialized = signal<boolean>(false);
  readonly error = signal<unknown>(null);

  readonly hasCompany = computed(() => this.companies().length > 0);
  readonly canPostJobs = computed(() => this.jobManagerCompanyIds().size > 0);
  readonly jobManagerCompanies = computed(() =>
    this.companies().filter((c) => this.jobManagerCompanyIds().has(c.id)),
  );
  readonly canCreateCompany = computed(
    () => this.ownedCompaniesCount() < ResourceLimit.MAX_COMPANIES,
  );

  /** The company whose pages are open (/companies/:companyId/...), if the user belongs to it. */
  readonly viewedCompanyId = computed<number | null>(() => {
    const match = /^\/companies\/(\d+)(?:[/?#]|$)/.exec(this.url() ?? '');
    const id = match ? Number(match[1]) : null;
    return id !== null && this.companies().some((c) => c.id === id) ? id : null;
  });

  /**
   * The company the navigation's company links lead to: the one being viewed, else the only one.
   * Null with several companies and none open: the links go to the list to pick one.
   */
  readonly linkCompanyId = computed<number | null>(() => {
    const companies = this.companies();
    return this.viewedCompanyId() ?? (companies.length === 1 ? companies[0].id : null);
  });

  /** Navigation link to a company page (jobs, team, ...), or to the company list to pick one. */
  companyLink(section: string): (string | number)[] {
    const id = this.linkCompanyId();
    return id === null ? ['/companies'] : ['/companies', id, section];
  }

  readonly jobListingsLink = computed(() => this.companyLink('jobs'));
  readonly teamLink = computed(() => this.companyLink('team'));
  readonly emailTemplatesLink = computed(() => this.companyLink('email-templates'));
  readonly settingsLink = computed(() => this.companyLink('settings'));

  private initPromise: Promise<void> | null = null;

  constructor() {
    if (this.keycloakSignal) {
      effect(() => {
        const event = this.keycloakSignal?.();
        if (!event) return;

        if (
          (event.type === KeycloakEventType.Ready && event.args === true) ||
          event.type === KeycloakEventType.AuthSuccess
        ) {
          if (!this.isInitialized()) {
            this.init().catch((err: unknown) => {
              console.debug('CompanyStateService auth event init failed', err);
            });
          }
        } else if (event.type === KeycloakEventType.AuthLogout) {
          this.resetState();
        }
      });
    }
  }

  async refresh(): Promise<void> {
    if (!this.keycloak.authenticated) {
      this.resetState();
      return;
    }

    try {
      this.error.set(null);
      // The list endpoint filters by a single role, so the job-manager roles are fetched separately
      const [allCompaniesRes, ownedCompaniesRes, coOwnedRes, hrManagedRes] = await Promise.all([
        firstValueFrom(this.companiesService.getCompanies(undefined, true)),
        firstValueFrom(this.companiesService.getCompanies(undefined, true, CompanyRole.Owner)),
        firstValueFrom(this.companiesService.getCompanies(undefined, true, CompanyRole.CoOwner)),
        firstValueFrom(this.companiesService.getCompanies(undefined, true, CompanyRole.HrManager)),
      ]);

      this.companies.set(allCompaniesRes?.content ?? []);
      this.jobManagerCompanyIds.set(
        new Set(
          [ownedCompaniesRes, coOwnedRes, hrManagedRes].flatMap(
            (res) => res?.content?.map((c) => c.id) ?? [],
          ),
        ),
      );
      const ownedTotal =
        ownedCompaniesRes?.page?.totalElements ?? ownedCompaniesRes?.content?.length ?? 0;
      this.ownedCompaniesCount.set(ownedTotal);
      this.isInitialized.set(true);
    } catch (err: unknown) {
      this.error.set(err);
    }
  }

  async init(): Promise<void> {
    if (this.isInitialized() || !this.keycloak.authenticated) {
      return;
    }
    if (this.initPromise) {
      return this.initPromise;
    }
    this.initPromise = (async () => {
      try {
        await this.refresh();
      } finally {
        this.initPromise = null;
      }
    })();
    return this.initPromise;
  }

  notifyCompanyCreated(createdCompany: CompanyResponse): void {
    this.companies.update((list) => [
      createdCompany,
      ...list.filter((c) => c.id !== createdCompany.id),
    ]);
    this.ownedCompaniesCount.update((count) => count + 1);
    this.jobManagerCompanyIds.update((ids) => new Set([...ids, createdCompany.id]));
    this.isInitialized.set(true);
    // Background refresh for accurate server pagination metadata
    this.refresh().catch((err: unknown) => {
      console.debug('Background refresh failed', err);
    });
  }

  notifyCompanyUpdated(updated: CompanyResponse): void {
    this.companies.update((list) => list.map((c) => (c.id === updated.id ? updated : c)));
  }

  notifyCompanyDeleted(deletedCompanyId: number): void {
    this.companies.update((list) => list.filter((c) => c.id !== deletedCompanyId));
    this.jobManagerCompanyIds.update(
      (ids) => new Set([...ids].filter((id) => id !== deletedCompanyId)),
    );
    this.ownedCompaniesCount.update((count) => Math.max(0, count - 1));
    this.isInitialized.set(true);
    this.refresh().catch((err: unknown) => {
      console.debug('Background refresh failed', err);
    });
  }

  private resetState(): void {
    this.companies.set([]);
    this.ownedCompaniesCount.set(0);
    this.jobManagerCompanyIds.set(new Set());
    this.isInitialized.set(false);
    this.error.set(null);
    this.initPromise = null;
  }
}
