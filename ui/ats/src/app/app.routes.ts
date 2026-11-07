import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';
import { pendingChangesGuard } from './guards/pending-changes.guard';
import { CompanyStateService } from './services/company-state.service';

export const routes: Routes = [
  {
    path: 'companies',
    canActivate: [authGuard],
    resolve: {
      companies: () => inject(CompanyStateService).init(),
    },
    loadComponent: () => import('./pages/company-list/company-list').then((m) => m.CompanyList),
  },
  {
    path: 'companies/:companyId/jobs',
    canActivate: [authGuard],
    resolve: {
      companies: () => inject(CompanyStateService).init(),
    },
    loadComponent: () => import('./pages/company-jobs/company-jobs').then((m) => m.CompanyJobs),
  },
  {
    path: 'companies/:companyId/jobs/:jobId/edit',
    canActivate: [authGuard],
    canDeactivate: [pendingChangesGuard],
    resolve: {
      companies: () => inject(CompanyStateService).init(),
    },
    loadComponent: () => import('./pages/job-create/job-create').then((m) => m.JobCreate),
  },
  {
    path: 'companies/:companyId/jobs/:jobId',
    canActivate: [authGuard],
    resolve: {
      companies: () => inject(CompanyStateService).init(),
    },
    loadComponent: () => import('./pages/job-pipeline/job-pipeline').then((m) => m.JobPipeline),
  },
  {
    path: 'companies/:companyId/team',
    canActivate: [authGuard],
    resolve: {
      companies: () => inject(CompanyStateService).init(),
    },
    loadComponent: () => import('./pages/company-team/company-team').then((m) => m.CompanyTeam),
  },
  {
    path: 'companies/:companyId/email-templates',
    canActivate: [authGuard],
    resolve: {
      companies: () => inject(CompanyStateService).init(),
    },
    loadComponent: () =>
      import('./pages/company-email-templates/company-email-templates').then(
        (m) => m.CompanyEmailTemplates,
      ),
  },
  {
    path: 'companies/:companyId/settings',
    canActivate: [authGuard],
    canDeactivate: [pendingChangesGuard],
    resolve: {
      companies: () => inject(CompanyStateService).init(),
    },
    loadComponent: () =>
      import('./pages/company-settings/company-settings').then((m) => m.CompanySettings),
  },
  {
    path: 'company/register',
    canActivate: [authGuard],
    canDeactivate: [pendingChangesGuard],
    resolve: {
      companies: () => inject(CompanyStateService).init(),
    },
    loadComponent: () =>
      import('./pages/company-register/company-register').then((m) => m.CompanyRegister),
  },
  {
    path: 'jobs',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/job-list/job-list').then((m) => m.JobList),
  },
  {
    path: 'jobs/new',
    canActivate: [authGuard],
    canDeactivate: [pendingChangesGuard],
    resolve: {
      companies: () => inject(CompanyStateService).init(),
    },
    loadComponent: () => import('./pages/job-create/job-create').then((m) => m.JobCreate),
  },
  {
    path: 'jobs/:id',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/job-detail/job-detail').then((m) => m.JobDetail),
  },
  {
    path: 'my-applications',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/my-applications/my-applications').then((m) => m.MyApplications),
  },
  {
    path: 'profile',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/profile/profile').then((m) => m.Profile),
  },
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./pages/landing/landing').then((m) => m.Landing),
  },
];
